package org.mskcc.kickoff.upload;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.mskcc.kickoff.upload.jira.state.IssueStatus;

import java.util.List;

public interface FileUploader {

    void upload(String summary, KickoffRequest kickoffRequest);

    void uploadFiles(KickoffRequest kickoffRequest, String key);

    void uploadSingleFile(KickoffRequest request, ManifestFile manifestFile, String key);

    void setIssueStatus(IssueStatus nextState);

    void assignUser(KickoffRequest kickoffRequest, String key);

    void changeStatus(String transitionName, String key);

    void deleteExistingFiles(KickoffRequest request, String key);

    List<JiraIssue.Fields.Attachment> getExistingManifestAttachments(KickoffRequest kickoffRequest, String key);
}
