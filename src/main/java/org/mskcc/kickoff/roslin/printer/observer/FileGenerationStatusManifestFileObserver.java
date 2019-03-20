package org.mskcc.kickoff.roslin.printer.observer;

import org.mskcc.kickoff.roslin.manifest.ManifestFile;
import org.mskcc.kickoff.roslin.notify.GenerationError;
import org.mskcc.kickoff.roslin.validator.ErrorRepository;
import org.springframework.stereotype.Component;

@Component
public class FileGenerationStatusManifestFileObserver implements ManifestFileObserver {
    private ErrorRepository errorRepository;

    public FileGenerationStatusManifestFileObserver(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public void updateGenerationStatus(ManifestFile manifestFile) {
        manifestFile.setFileGenerated(true);
    }

    @Override
    public void updateFileError(ManifestFile manifestFile, GenerationError generationError) {
        manifestFile.addGenerationError(generationError);
    }
}
