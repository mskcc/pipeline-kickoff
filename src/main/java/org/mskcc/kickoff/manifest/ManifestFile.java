package org.mskcc.kickoff.manifest;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.*;
import org.mskcc.kickoff.printer.observer.FileGenerationStatusManifestFileObserver;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

public enum ManifestFile {
    MAPPING("Mapping file"),
    PAIRING("Pairing file"),
    SAMPLE_KEY("Sample Key file"),
    PATIENT("Patient file"),
    CLINICAL("Clinical file"),
    GROUPING("Grouping file"),
    REQUEST("Request file"),
    README("Readme file"),
    MANIFEST("Manifest file"),
    C_TO_P_MAPPING("C to p mapping file");

    private static List<ManifestFile> requiredFiles = new ArrayList<>();

    private final String name;

    private FilePrinter filePrinter;
    private boolean fileGenerated;
    private List<GenerationError> generationErrors = new ArrayList<>();

    ManifestFile(String name) {
        this.name = name;
    }

    public static List<ManifestFile> getRequiredFiles() {
        return requiredFiles;
    }

    public static void setRequiredFiles(List<ManifestFile> requiredFiles) {
        ManifestFile.requiredFiles = requiredFiles;
    }

    public FilePrinter getFilePrinter() {
        return filePrinter;
    }

    public void setFilePrinter(FilePrinter filePrinter) {
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

    public List<GenerationError> getGenerationErrors() {
        return generationErrors;
    }

    public void addGenerationError(GenerationError generationError) {
        this.generationErrors.add(generationError);
    }

    public String getFilePath(KickoffRequest kickoffRequest) {
        return filePrinter.getFilePath(kickoffRequest);
    }

    @Override
    public String toString() {
        return name;
    }

    public static class FilePrinterInjector {
        @Autowired
        private FileGenerationStatusManifestFileObserver fileGenerationStatusObserver;

        @Autowired
        private GroupingFilePrinter groupingFilePrinter;

        @Autowired
        private MappingFilePrinter mappingFilePrinter;

        @Autowired
        private PairingFilePrinter pairingFilePrinter;

        @Autowired
        private RequestFilePrinter requestFilePrinter;

        @Autowired
        private SampleKeyPrinter sampleKeyPrinter;

        @Autowired
        private DataClinicalFilePrinter dataClinicalFilePrinter;

        @Autowired
        private PatientFilePrinter patientFilePrinter;

        @Autowired
        private ReadMePrinter readMePrinter;

        @Autowired
        private CidToPidMappingPrinter cidToPidMappingPrinter;

        @Autowired
        private ManifestFilePrinter manifestFilePrinter;

        @PostConstruct
        public void init() {
            initObservers();
            ManifestFile.GROUPING.setFilePrinter(groupingFilePrinter);
            ManifestFile.MAPPING.setFilePrinter(mappingFilePrinter);
            ManifestFile.PAIRING.setFilePrinter(pairingFilePrinter);
            ManifestFile.REQUEST.setFilePrinter(requestFilePrinter);
            ManifestFile.SAMPLE_KEY.setFilePrinter(sampleKeyPrinter);
            ManifestFile.CLINICAL.setFilePrinter(dataClinicalFilePrinter);
            ManifestFile.PATIENT.setFilePrinter(patientFilePrinter);
            ManifestFile.README.setFilePrinter(readMePrinter);
            ManifestFile.C_TO_P_MAPPING.setFilePrinter(cidToPidMappingPrinter);
            ManifestFile.MANIFEST.setFilePrinter(manifestFilePrinter);
        }

        private void initObservers() {
            groupingFilePrinter.register(fileGenerationStatusObserver);
            mappingFilePrinter.register(fileGenerationStatusObserver);
            pairingFilePrinter.register(fileGenerationStatusObserver);
            requestFilePrinter.register(fileGenerationStatusObserver);
            dataClinicalFilePrinter.register(fileGenerationStatusObserver);
            patientFilePrinter.register(fileGenerationStatusObserver);
            readMePrinter.register(fileGenerationStatusObserver);
            cidToPidMappingPrinter.register(fileGenerationStatusObserver);
            manifestFilePrinter.register(fileGenerationStatusObserver);
        }
    }
}
