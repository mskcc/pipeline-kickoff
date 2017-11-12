package org.mskcc.kickoff.printer;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class OutputFilesPrinter {
    public void print(KickoffRequest kickoffRequest) {
        Set<FilePrinter> filePrinters = getFilePrinters();

        for (FilePrinter filePrinter : filePrinters) {
            if (filePrinter.shouldPrint(kickoffRequest))
                filePrinter.print(kickoffRequest);
        }
    }

    private Set<FilePrinter> getFilePrinters() {
        Set<FilePrinter> filePrinters = new LinkedHashSet<>();

        for (ManifestFile manifestFile : ManifestFile.values())
            filePrinters.add(manifestFile.getFilePrinter());
        return filePrinters;
    }
}
