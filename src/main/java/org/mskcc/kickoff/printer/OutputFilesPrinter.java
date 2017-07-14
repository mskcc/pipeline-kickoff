package org.mskcc.kickoff.printer;

import org.mskcc.kickoff.domain.Request;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class OutputFilesPrinter {
    //@TODO think ghow to deal with files created and then delete them
    static final Set<File> filesCreated = new HashSet<>();
    private final PatientFilePrinter patientFilePrinter = new PatientFilePrinter();
    private final ClinicalFilePrinter clinicalFilePrinter = new ClinicalFilePrinter();
    private final GroupingFilePrinter groupingFilePrinter = new GroupingFilePrinter();
    private final PairingFilePrinter pairingFilePrinter = new PairingFilePrinter();
    private Set<FilePrinter> filePrinters = new LinkedHashSet<>();
    private MappingFilePrinter mappingFilePrinter;
    private RequestFilePrinter requestFilePrinter = new RequestFilePrinter();
    private ReadMePrinter readMePrinter = new ReadMePrinter();
    private ManifestFilePrinter manifestFilePrinter = new ManifestFilePrinter();
    private SampleKeyPrinter sampleKeyPrinter;

    public OutputFilesPrinter(MappingFilePrinter mappingFilePrinter, SampleKeyPrinter sampleKeyPrinter) {
        this.mappingFilePrinter = mappingFilePrinter;
        this.sampleKeyPrinter = sampleKeyPrinter;
        init();
    }

    private void init() {
        //@TODO subscribe to files printer?
        filePrinters.add(mappingFilePrinter);
        filePrinters.add(requestFilePrinter);
        filePrinters.add(manifestFilePrinter);
        filePrinters.add(patientFilePrinter);
        filePrinters.add(clinicalFilePrinter);
        filePrinters.add(groupingFilePrinter);
        filePrinters.add(pairingFilePrinter);
        filePrinters.add(readMePrinter);
        filePrinters.add(sampleKeyPrinter);
    }

    public void print(Request request) {
        for (FilePrinter filePrinter : filePrinters) {
            if (filePrinter.shouldPrint(request))
                filePrinter.print(request);
        }
    }

    public Set<File> getFilesCreated() {
        return filesCreated;
    }
}
