package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.jira.JiraFileUploader;

public class RegenerateFilesState implements JiraIssueState {
    private final String name;
    private final String transitionName;
    private final JiraIssueState nextState;

    public RegenerateFilesState(String name, String transitionName, JiraIssueState nextState) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        jiraFileUploader.deleteExistingFiles(kickoffRequest);
        jiraFileUploader.uploadFiles(kickoffRequest);
        jiraFileUploader.setJiraIssueState(nextState);
        jiraFileUploader.assignUser(kickoffRequest);
        jiraFileUploader.changeStatus(transitionName, kickoffRequest);
    }

    @Override
    public String getName() {
        return name;
    }
}
