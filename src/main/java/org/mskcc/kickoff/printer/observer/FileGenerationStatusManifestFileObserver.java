package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.validator.ErrorRepository;
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
