package org.mskcc.kickoff.upload;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;

public interface FileUploader {
    void deleteExistingFiles(KickoffRequest request);

    void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile);

    void upload(KickoffRequest kickoffRequest);
}
