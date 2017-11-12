package org.mskcc.kickoff.notify;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.springframework.stereotype.Component;

@Component
public class FilePrinterObserver implements Observer {
    @Override
    public void update(ManifestFile manifestFile, FileGenerated event) {
        manifestFile.setFileGenerated(true);
    }

    @Override
    public void update(ManifestFile manifestFile, GenerationError event, String errorMessage) {
        manifestFile.addGenerationError(errorMessage);
    }
}
