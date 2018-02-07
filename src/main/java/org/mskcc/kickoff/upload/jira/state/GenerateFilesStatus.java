package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.ToHoldTransitioner;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class GenerateFilesStatus implements IssueStatus {
    private final String name;
    private final String transitionName;
    private final IssueStatus nextState;

    @Autowired
    public ToHoldTransitioner toHoldJiraTransitioner;

    public GenerateFilesStatus(String name, String transitionName, IssueStatus nextState) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader) {
        validateNoManifestFilesExists(kickoffRequest, jiraFileUploader);

        jiraFileUploader.uploadFiles(kickoffRequest);
        jiraFileUploader.setIssueStatus(nextState);
        jiraFileUploader.assignUser(kickoffRequest);
        jiraFileUploader.changeStatus(transitionName, issueId);
        toHoldJiraTransitioner.transition(jiraFileUploader, issueId);
    }

    private void validateNoManifestFilesExists(KickoffRequest kickoffRequest, FileUploader
            jiraFileUploader) {
        List<JiraIssue.Fields.Attachment> existingManifestAttachments = jiraFileUploader
                .getExistingManifestAttachments(kickoffRequest);

        if (existingManifestAttachments.size() != 0)
            throw new RuntimeException(String.format("This is initial manifest files generation. No files should be " +
                    "uploaded to issue. No new files will be uploaded. Manifest files are already uploaded to " +
                    "jira: %s", existingManifestAttachments));
    }

    @Override
    public String getName() {
        return name;
    }
}
