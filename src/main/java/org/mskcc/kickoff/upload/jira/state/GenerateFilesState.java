package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;
import org.mskcc.kickoff.upload.jira.ToHoldJiraTransitioner;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class GenerateFilesState implements JiraIssueState {
    private final String name;
    private final String transitionName;
    private final JiraIssueState nextState;

    @Autowired
    public ToHoldJiraTransitioner toHoldJiraTransitioner;

    public GenerateFilesState(String name, String transitionName, JiraIssueState nextState) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        validateNoManifestFilesExists(kickoffRequest, jiraFileUploader);

        jiraFileUploader.uploadFiles(kickoffRequest);
        jiraFileUploader.setJiraIssueState(nextState);
        jiraFileUploader.assignUser(kickoffRequest);
        jiraFileUploader.changeStatus(transitionName, kickoffRequest);
        toHoldJiraTransitioner.transition(kickoffRequest, jiraFileUploader);
    }

    private void validateNoManifestFilesExists(KickoffRequest kickoffRequest, JiraFileUploader
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
