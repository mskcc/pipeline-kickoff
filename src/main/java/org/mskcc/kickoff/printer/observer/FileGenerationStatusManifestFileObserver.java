package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.FileGenerated;
import org.mskcc.kickoff.notify.GenerationError;
import org.springframework.stereotype.Component;

@Component
public class FileGenerationStatusManifestFileObserver implements ManifestFileObserver {
    @Override
    public void update(KickoffRequest request, ManifestFile manifestFile, FileGenerated event) {
        manifestFile.setFileGenerated(true);
    }

    @Override
    public void update(KickoffRequest request, ManifestFile manifestFile, GenerationError event, String errorMessage) {
        manifestFile.addGenerationError(errorMessage);
    }
}
