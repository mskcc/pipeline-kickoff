package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;

public interface ManifestFileObserver {
    void updateGenerationStatus(ManifestFile manifestFile);

    void updateFileError(ManifestFile manifestFile, GenerationError generationError);
}
