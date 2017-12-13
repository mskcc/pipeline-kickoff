package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.FileGenerated;
import org.mskcc.kickoff.notify.GenerationError;

public interface ManifestFileObserver {
    void update(KickoffRequest request, ManifestFile manifestFile, FileGenerated event);

    void update(KickoffRequest request, ManifestFile manifestFile, GenerationError event, String errorMessage);
}
