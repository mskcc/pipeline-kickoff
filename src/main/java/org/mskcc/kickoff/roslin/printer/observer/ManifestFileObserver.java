package org.mskcc.kickoff.roslin.printer.observer;

import org.mskcc.kickoff.roslin.manifest.ManifestFile;
import org.mskcc.kickoff.roslin.notify.GenerationError;

public interface ManifestFileObserver {
    void updateGenerationStatus(ManifestFile manifestFile);

    void updateFileError(ManifestFile manifestFile, GenerationError generationError);
}
