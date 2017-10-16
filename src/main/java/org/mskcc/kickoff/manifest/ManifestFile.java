package org.mskcc.kickoff.manifest;

import org.mskcc.kickoff.generator.PairingsResolver;
import org.mskcc.kickoff.notify.FilePrinterObserver;
import org.mskcc.kickoff.printer.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

public enum ManifestFile {
    MAPPING("Mapping file"),
    PAIRING("Pairing file"),
    SAMPLE_KEY("Sample Key file"),
    PATIENT("Patient file", new PatientFilePrinter()),
    CLINICAL("Clinical file", new ClinicalFilePrinter()),
    GROUPING("Grouping file", new GroupingFilePrinter()),
    REQUEST("Request file", new RequestFilePrinter()),
    README("Readme file", new ReadMePrinter()),
    MANIFEST("Manifest file", new ManifestFilePrinter()),
    C_TO_P_MAPPING("C to p mapping file", new CidToPidMappingPrinter());

    private final String name;

    private FilePrinter filePrinter;

    private boolean fileGenerated;
    private List<String> generationErrors = new ArrayList<>();

    ManifestFile(String name, FilePrinter filePrinter) {
        this.name = name;
        this.filePrinter = filePrinter;
    }

    ManifestFile(String name) {
        this.name = name;
    }

    public FilePrinter getFilePrinter() {
        return filePrinter;
    }

    private void setFilePrinter(FilePrinter filePrinter) {
        this.filePrinter = filePrinter;
    }

    public String getName() {
        return name;
    }

    public boolean isFileGenerated() {
        return fileGenerated;
    }

    public void setFileGenerated(boolean fileGenerated) {
        this.fileGenerated = fileGenerated;
    }

    public List<String> getGenerationErrors() {
        return generationErrors;
    }

    public void addGenerationError(String generationError) {
        this.generationErrors.add(generationError);
    }

    @Override
    public String toString() {
        return name;
    }

    @Component
    public static class FilePrinterInjector {
        @Autowired
        private PairingsResolver pairingsResolver;

        @Autowired
        private FilePrinterObserver filePrinterObserver;

        @Autowired
        private MappingFilePrinter mappingFilePrinter;

        @Autowired
        private SampleKeyPrinter sampleKeyPrinter;

        @PostConstruct
        public void init() {
            ManifestFile.MAPPING.setFilePrinter(getMappingFilePrinter());
            ManifestFile.PAIRING.setFilePrinter(new PairingFilePrinter(pairingsResolver));
            ManifestFile.SAMPLE_KEY.setFilePrinter(sampleKeyPrinter);
        }

        private FilePrinter getMappingFilePrinter() {
            mappingFilePrinter.register(filePrinterObserver);
            return mappingFilePrinter;
        }
    }
}
