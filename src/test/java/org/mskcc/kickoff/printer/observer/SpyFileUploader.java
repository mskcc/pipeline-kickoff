package org.mskcc.kickoff.printer.observer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.upload.FileUploader;

import java.util.ArrayList;
import java.util.List;

public class SpyFileUploader implements FileUploader {
    private Multimap<KickoffRequest, ManifestFile> uploadedFiles = HashMultimap.create();
    private List<KickoffRequest> requestsWithDeletedFiles = new ArrayList<>();

    @Override
    public void deleteExistingFiles(KickoffRequest request) {
        requestsWithDeletedFiles.add(request);
    }

    @Override
    public void upload(KickoffRequest request, ManifestFile manifestFile) {
        uploadedFiles.put(request, manifestFile);
    }

    public Multimap<KickoffRequest, ManifestFile> getUploadedFiles() {
        return uploadedFiles;
    }

    public List<KickoffRequest> getRequestsWithDeletedFiles() {
        return requestsWithDeletedFiles;
    }
}
