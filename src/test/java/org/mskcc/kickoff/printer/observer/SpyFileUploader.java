package org.mskcc.kickoff.printer.observer;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.upload.jira.state.IssueStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpyFileUploader implements FileUploader {
    private Multimap<KickoffRequest, ManifestFile> uploadedFiles = HashMultimap.create();
    private List<KickoffRequest> requestsWithDeletedFiles = new ArrayList<>();

    @Override
    public void deleteExistingFiles(KickoffRequest request, String key) {
        requestsWithDeletedFiles.add(request);
    }

    @Override
    public void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile, String requestId) {
        uploadedFiles.put(request, manifestFile);
    }

    @Override
    public void upload(String summary, KickoffRequest kickoffRequest) {
        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            uploadSingleFile(kickoffRequest, manifestFile, kickoffRequest.getId());
        }
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, String requestId) {

    }

    @Override
    public void setIssueStatus(IssueStatus nextState) {

    }

    @Override
    public void assignUser(KickoffRequest kickoffRequest, String requestId) {

    }

    @Override
    public void changeStatus(String transitionName, String issueId) {

    }

    @Override
    public List<JiraIssue.Fields.Attachment> getExistingManifestAttachments(KickoffRequest kickoffRequest, String
            requestId) {
        return Collections.emptyList();
    }

    public Multimap<KickoffRequest, ManifestFile> getUploadedFiles() {
        return uploadedFiles;
    }

    public List<KickoffRequest> getRequestsWithDeletedFiles() {
        return requestsWithDeletedFiles;
    }
}
