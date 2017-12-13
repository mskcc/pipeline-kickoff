package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.FileGenerated;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.upload.FileUploader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FileUploadingManifestFileObserver implements ManifestFileObserver {
    private final FileUploader fileUploader;

    @Autowired
    public FileUploadingManifestFileObserver(FileUploader fileUploader) {
        this.fileUploader = fileUploader;
    }

    @Override
    public void update(KickoffRequest request, ManifestFile manifestFile, FileGenerated event) {
        fileUploader.upload(request, manifestFile);
    }

    @Override
    public void update(KickoffRequest request, ManifestFile manifestFile, GenerationError event, String errorMessage) {
        // do nothing on file generation error
    }
}
