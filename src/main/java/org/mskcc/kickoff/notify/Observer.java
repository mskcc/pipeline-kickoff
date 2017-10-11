package org.mskcc.kickoff.notify;

import org.mskcc.kickoff.manifest.ManifestFile;

public interface Observer {
    void update(ManifestFile manifestFile, FileGenerated event);

    void update(ManifestFile manifestFile, GenerationError event, String errorMessage);
}
