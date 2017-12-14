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
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.util.email.EmailNotificator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;

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

            fileUploader.deleteExistingFiles(kickoffRequest);
            uploadFiles(kickoffRequest);
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        } finally {
            setFilePermissions(kickoffRequest);
            sendEmailIfFileNotCreated(projectId);
        }
    }

    private void uploadFiles(KickoffRequest kickoffRequest) {
        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            if (manifestFile.isFileGenerated())
                fileUploader.upload(kickoffRequest, manifestFile);
        }
    }

    private void sendEmailIfFileNotCreated(String projectId) {
        StringBuilder stringBuilder = new StringBuilder();
        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            if (!manifestFile.isFileGenerated())
                stringBuilder.append(String.format("%s\n", manifestFile.getName()));
        }

        String errors = stringBuilder.toString();
        if (!StringUtils.isEmpty(errors)) {
            try {
                DEV_LOGGER.info(String.format("Sending email notification about manifest files not generated for " +
                        "request: %s", projectId));

                emailNotificator.notifyMessage(projectId, errors);
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
