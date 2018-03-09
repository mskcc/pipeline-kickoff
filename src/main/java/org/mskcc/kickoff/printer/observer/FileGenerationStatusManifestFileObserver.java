package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.springframework.stereotype.Component;

@Component
public class FileGenerationStatusManifestFileObserver implements ManifestFileObserver {
    @Override
    public void updateGenerationStatus(ManifestFile manifestFile) {
        manifestFile.setFileGenerated(true);
    }

    @Override
    public void updateFileError(ManifestFile manifestFile, GenerationError generationError) {
        manifestFile.addGenerationError(generationError);
    }
}
