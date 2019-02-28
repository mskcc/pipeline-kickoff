package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.JiraFileUploader;
import org.mskcc.kickoff.upload.jira.domain.JiraIssue;

import java.util.List;

public class GenerateFilesStatus implements IssueStatus {
    private final String name;
    private final String transitionName;
    private final IssueStatus nextState;

    public GenerateFilesStatus(String name, String transitionName, IssueStatus nextState) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader jiraFileUploader, String key, String summary) {
        validateNoManifestFilesExists(kickoffRequest, jiraFileUploader, key);

        jiraFileUploader.uploadFiles(kickoffRequest, key);
        jiraFileUploader.setIssueStatus(nextState);
        jiraFileUploader.assignUser(kickoffRequest, key);
        jiraFileUploader.changeStatus(transitionName, key);
    }

    @Override
    public void validateInputs(String key, String summary, JiraFileUploader jiraFileUploader) {
        throw new IllegalStateException(String.format("Files cannot be validated in state: %s. They haven't been " +
                "generated yet.", getName()));
    }

    private void validateNoManifestFilesExists(KickoffRequest kickoffRequest, FileUploader
            jiraFileUploader, String key) {
        List<JiraIssue.Fields.Attachment> existingManifestAttachments = jiraFileUploader
                .getExistingManifestAttachments(kickoffRequest, key);

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
