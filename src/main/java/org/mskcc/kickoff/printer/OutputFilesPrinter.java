package org.mskcc.kickoff.printer;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.PairingsResolver;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class OutputFilesPrinter {
    private final PatientFilePrinter patientFilePrinter = new PatientFilePrinter();
    private final ClinicalFilePrinter clinicalFilePrinter = new ClinicalFilePrinter();
    private final GroupingFilePrinter groupingFilePrinter = new GroupingFilePrinter();
    private final PairingFilePrinter pairingFilePrinter;
    private Set<FilePrinter> filePrinters = new LinkedHashSet<>();
    private MappingFilePrinter mappingFilePrinter;
    private RequestFilePrinter requestFilePrinter = new RequestFilePrinter();
    private ReadMePrinter readMePrinter = new ReadMePrinter();
    private ManifestFilePrinter manifestFilePrinter = new ManifestFilePrinter();
    private SampleKeyPrinter sampleKeyPrinter;
    private CidToPidMappingPrinter cidToPidMappingPrinter = new CidToPidMappingPrinter();

    public OutputFilesPrinter(PairingsResolver pairingsResolver, MappingFilePrinter mappingFilePrinter, SampleKeyPrinter sampleKeyPrinter) {
        this.mappingFilePrinter = mappingFilePrinter;
        this.sampleKeyPrinter = sampleKeyPrinter;
        pairingFilePrinter = new PairingFilePrinter(pairingsResolver);
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
        filePrinters.add(cidToPidMappingPrinter);
    }

    public void print(KickoffRequest kickoffRequest) {
        for (FilePrinter filePrinter : filePrinters) {
            if (filePrinter.shouldPrint(kickoffRequest))
                filePrinter.print(kickoffRequest);
        }
    }
}
