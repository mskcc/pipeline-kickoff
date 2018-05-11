package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.FilePermissionConfigurator;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.notify.NotificationFormatter;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.runAsExome;

@Component
public class FileManifestGenerator implements ManifestGenerator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private OutputFilesPrinter outputFilesPrinter;

    @Autowired
    private FilesArchiver filesArchiver;

    @Autowired
    private RequestValidator requestValidator;

    @Autowired
    private LogConfigurator logConfigurator;

    @Autowired
    private OutputDirRetriever outputDirRetriever;

    @Autowired
    private RequestProxy requestProxy;

    @Autowired
    private ProjectNameValidator projectNameValidator;

    @Autowired
    private EmailNotificator emailNotificator;

    @Autowired
    private FileUploader fileUploader;

    @Autowired
    private NotificationFormatter notificationFormatter;

    @Autowired
    private ErrorRepository errorRepository;

    @Override
    public void generate(String projectId) throws Exception {
        KickoffRequest kickoffRequest = null;

        try {
            validateProjectName(projectId);
            String projectFilePath = getOutputPath(projectId);
            logConfigurator.configureProjectLog(projectFilePath);

            kickoffRequest = getRequest(projectId, projectFilePath);
            createOutputDir(kickoffRequest);
            requestValidator.validate(kickoffRequest);
            resolveExomeRequestType(kickoffRequest);

            saveFiles(kickoffRequest);
            uploadFiles(kickoffRequest);
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        } finally {
            setFilePermissions(kickoffRequest);
            sendEmailIfFileNotCreated(projectId);
        }
    }

    private void uploadFiles(KickoffRequest kickoffRequest) {
        fileUploader.upload(kickoffRequest);
    }

    private void sendEmailIfFileNotCreated(String projectId) {
        List<ManifestFile> notGenerated = ManifestFile.getRequiredFiles().stream()
                .filter(f -> !f.isFileGenerated())
                .collect(Collectors.toList());

        String errorMessage = getGeneralErrors();
        if (notGenerated.size() > 0) {
            DEV_LOGGER.info(String.format("Sending email notification about manifest files not generated for " +
                    "request: %s", projectId));

            errorMessage += notificationFormatter.format(notGenerated);
        }

        if (!StringUtils.isEmpty(errorMessage)) {
            try {
                emailNotificator.notifyMessage(projectId, errorMessage);
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Unable to send email notification about not generated manifest files " +
                                "for request: %s.",
                        projectId), e);
            }
        }
    }

    private String getGeneralErrors() {
        StringBuilder generalErrors = new StringBuilder();

        if (errorRepository.getErrors().size() > 0)
            generalErrors.append("General request errors: \n");

        for (GenerationError generationError : errorRepository.getErrors()) {
            generalErrors.append(generationError.getMessage()).append("\n");
        }

        return generalErrors.toString();
    }

    private void setFilePermissions(KickoffRequest kickoffRequest) {
        if (kickoffRequest != null) {
            File f = new File(kickoffRequest.getOutputPath());
            FilePermissionConfigurator.setPermissions(f);
        }
    }

    private KickoffRequest getRequest(String projectId, String projectFilePath) throws Exception {
        KickoffRequest request = requestProxy.getRequest(projectId);
        request.setOutputPath(projectFilePath);
        return request;
    }

    private String getOutputPath(String projectId) {
        return outputDirRetriever.retrieve(projectId, Arguments.outdir);
    }

    private void validateProjectName(String projectId) {
        projectNameValidator.validate(projectId);
    }


    private void saveFiles(KickoffRequest kickoffRequest) {
        kickoffRequest.archiveFilesToOld();
        outputFilesPrinter.print(kickoffRequest);
        filesArchiver.archive(kickoffRequest);
    }

    private void resolveExomeRequestType(KickoffRequest kickoffRequest) {
        if (kickoffRequest.getRequestType() == RequestType.IMPACT && runAsExome)
            kickoffRequest.setRequestType(RequestType.EXOME);
    }

    private void createOutputDir(KickoffRequest request) {
        if (request.getRequestType() == RequestType.RNASEQ || request.getRequestType() == RequestType.OTHER) {
            String message = String.format("Output path: %s", request.getOutputPath());
            PM_LOGGER.info(message);
            DEV_LOGGER.info(message);

            if (!request.isForced() && request.isManualDemux()) {
                String message1 = "Manual demux performed. I will not output maping file";
                PM_LOGGER.log(PmLogPriority.WARNING, message1);
                DEV_LOGGER.warn(message1);
            }
        } else {
            File outputPath = new File(request.getOutputPath());
            outputPath.mkdirs();
        }
    }
}
