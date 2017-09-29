package org.mskcc.kickoff.generator;

import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.config.FilePermissionConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.RequestValidator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

import static org.mskcc.kickoff.config.Arguments.runAsExome;

public class FileManifestGenerator implements ManifestGenerator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private OutputFilesPrinter outputFilesPrinter;

    @Autowired
    private FilesArchiver filesArchiver;

    @Autowired
    private RequestValidator requestValidator;

    @Override
    public void generate(KickoffRequest kickoffRequest) throws Exception {
        try {
            createOutputDir(kickoffRequest);
            requestValidator.validate(kickoffRequest);
            resolveExomeRequestType(kickoffRequest);

            saveFiles(kickoffRequest);
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        } finally {
            File f = new File(kickoffRequest.getOutputPath());
            FilePermissionConfigurator.setPermissions(f);
        }
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
