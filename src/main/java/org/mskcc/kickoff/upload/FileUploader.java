package org.mskcc.kickoff.upload;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;

public interface FileUploader {
    void deleteExistingFiles(KickoffRequest request) throws FileDeletionException;

    void upload(KickoffRequest request, ManifestFile manifestFile);
}
