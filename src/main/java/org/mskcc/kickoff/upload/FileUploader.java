package org.mskcc.kickoff.upload;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.upload.jira.state.IssueStatus;

import java.util.List;

public interface FileUploader {
    void deleteExistingFiles(KickoffRequest request);

    void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile);

    void upload(KickoffRequest kickoffRequest);

    void uploadFiles(KickoffRequest kickoffRequest);

    void setIssueStatus(IssueStatus nextState);

    void assignUser(KickoffRequest kickoffRequest);

    void changeStatus(String transitionName, String issueId);

    List<JiraIssue.Fields.Attachment> getExistingManifestAttachments(KickoffRequest kickoffRequest);
}
