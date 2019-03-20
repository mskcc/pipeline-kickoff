package org.mskcc.kickoff.roslin.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.roslin.archive.FilesArchiver;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.FilePermissionConfigurator;
import org.mskcc.kickoff.logger.LogConfigurator;
import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.roslin.manifest.ManifestFile;
import org.mskcc.kickoff.roslin.notify.GenerationError;
import org.mskcc.kickoff.roslin.notify.NotificationFormatter;
import org.mskcc.kickoff.roslin.printer.ErrorCode;
import org.mskcc.kickoff.roslin.printer.OutputFilesPrinter;
import org.mskcc.kickoff.roslin.proxy.RequestProxy;
import org.mskcc.kickoff.roslin.retriever.RequestNotFoundException;
import org.mskcc.kickoff.roslin.util.Constants;
import org.mskcc.kickoff.roslin.upload.FileUploader;
import org.mskcc.kickoff.roslin.validator.ErrorRepository;
import org.mskcc.kickoff.roslin.validator.ProjectNameValidator;
import org.mskcc.kickoff.roslin.validator.RequestValidator;
import org.mskcc.kickoff.roslin.velox.ProjectRetrievalException;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

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
    @Qualifier("singleSlash")
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
        } catch (RequestNotFoundException | ProjectRetrievalException e) {
            DEV_LOGGER.error(e.getMessage(), e);
            errorRepository.add(new GenerationError(e.getMessage(), ErrorCode.PROJECT_RETRIEVE_ERROR));
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        } finally {
            setFilePermissions(kickoffRequest);
            sendEmailIfFileNotCreated(projectId);
        }

        uploadFiles(projectId, kickoffRequest);
    }

    private void uploadFiles(String projectId, KickoffRequest kickoffRequest) {
        fileUploader.upload(projectId, kickoffRequest);
    }

    private void sendEmailIfFileNotCreated(String projectId) {
        List<ManifestFile> notGenerated = ManifestFile.getRequiredFiles().stream()
                .filter(f -> !f.isFileGenerated())
                .collect(Collectors.toList());

        String errorMessage = "";
        if (notGenerated.size() > 0) {
            DEV_LOGGER.info(String.format("Sending email notification about manifest files not generated for " +
                    "request: %s", projectId));

            errorMessage += notificationFormatter.format();
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
        removeOldFiles(kickoffRequest);
        filesArchiver.archive(kickoffRequest);
    }

    private void removeOldFiles(KickoffRequest kickoffRequest) {
        for (ManifestFile manifestFile : ManifestFile.values()) {
            if (Files.exists(Paths.get(manifestFile.getFilePath(kickoffRequest))) && !manifestFile.isFileGenerated()) {
                removeFile(manifestFile.getFilePath(kickoffRequest));
            }
        }
    }

    private void removeFile(String filePath) {
        try {
            Files.delete(Paths.get(filePath));
            DEV_LOGGER.info(String.format("Removed old file: %s", filePath));
        } catch (IOException e) {
            DEV_LOGGER.warn(String.format("Unable to delete old file: %s", filePath));
        }
    }

    private void resolveExomeRequestType(KickoffRequest kickoffRequest) {
        if (kickoffRequest.getRequestType() == RequestType.IMPACT && Arguments.runAsExome)
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
