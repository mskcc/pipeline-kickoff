package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;

public class RegenerateFilesStatus implements IssueStatus {
    private final String name;
    private final String transitionName;
    private final IssueStatus nextState;

    public RegenerateFilesStatus(String name, String transitionName, IssueStatus nextState) {
        this.name = name;
        this.transitionName = transitionName;
        this.nextState = nextState;
    }

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader) {
        fileUploader.deleteExistingFiles(kickoffRequest);
        fileUploader.uploadFiles(kickoffRequest);
        fileUploader.setIssueStatus(nextState);
        fileUploader.assignUser(kickoffRequest);
        fileUploader.changeStatus(transitionName, issueId);
    }

    @Override
    public String getName() {
        return name;
    }
}
