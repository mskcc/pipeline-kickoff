package org.mskcc.kickoff.manifest;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.*;
import org.mskcc.kickoff.printer.observer.FileGenerationStatusManifestFileObserver;
import org.mskcc.kickoff.printer.observer.FileUploadingManifestFileObserver;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

public enum ManifestFile {
    MAPPING("Mapping file"),
    PAIRING("Pairing file"),
    SAMPLE_KEY("Sample Key file"),
    PATIENT("Patient file", new PatientFilePrinter()),
    CLINICAL("Clinical file", new ClinicalFilePrinter()),
    GROUPING("Grouping file"),
    REQUEST("Request file"),
    README("Readme file", new ReadMePrinter()),
    MANIFEST("Manifest file", new ManifestFilePrinter()),
    C_TO_P_MAPPING("C to p mapping file", new CidToPidMappingPrinter());

    private static List<ManifestFile> requiredFiles = new ArrayList<>();

    static {
        requiredFiles.add(MAPPING);
        requiredFiles.add(GROUPING);
        requiredFiles.add(PAIRING);
        requiredFiles.add(REQUEST);
    }

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

    public static List<ManifestFile> getRequiredFiles() {
        return requiredFiles;
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

    public List<String> getGenerationErrors() {
        return generationErrors;
    }

    public void addGenerationError(String generationError) {
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
        private FileUploadingManifestFileObserver fileUploadingManifestFileObserver;

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

        @PostConstruct
        public void init() {
            initObservers();
            ManifestFile.GROUPING.setFilePrinter(groupingFilePrinter);
            ManifestFile.MAPPING.setFilePrinter(mappingFilePrinter);
            ManifestFile.PAIRING.setFilePrinter(pairingFilePrinter);
            ManifestFile.REQUEST.setFilePrinter(requestFilePrinter);
            ManifestFile.SAMPLE_KEY.setFilePrinter(sampleKeyPrinter);
        }

        private void initObservers() {
            groupingFilePrinter.register(fileGenerationStatusObserver);
            mappingFilePrinter.register(fileGenerationStatusObserver);
            pairingFilePrinter.register(fileGenerationStatusObserver);
            requestFilePrinter.register(fileGenerationStatusObserver);
        }
    }
}
