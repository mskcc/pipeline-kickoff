package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.domain.KickoffRequest;

import java.util.List;

public class GenerateFilesState implements JiraIssueState {
    private final String name;
    private final String transitionName;
    private final JiraIssueState nextState;

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
        jiraFileUploader.changeStatus(transitionName, kickoffRequest);
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
