package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.util.Constants;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class OutputFilesPrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public void print(KickoffRequest kickoffRequest) {
        DEV_LOGGER.info("Starting to create output files");

        Set<FilePrinter> filePrinters = getFilePrinters();
        for (FilePrinter filePrinter : filePrinters) {
            try {
                if (filePrinter.shouldPrint(kickoffRequest))
                    filePrinter.print(kickoffRequest);
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Unable to save file: %s", filePrinter.getFilePath(kickoffRequest)));
            }
        }
    }

    private Set<FilePrinter> getFilePrinters() {
        Set<FilePrinter> filePrinters = new LinkedHashSet<>();

        for (ManifestFile manifestFile : ManifestFile.values())
            filePrinters.add(manifestFile.getFilePrinter());
        return filePrinters;
    }
}
