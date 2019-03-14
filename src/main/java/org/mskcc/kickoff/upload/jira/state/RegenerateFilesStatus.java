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
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String key, String summary) {
        if (validateBefore(kickoffRequest, fileUploader, key)) {
            fileUploader.deleteExistingFiles(kickoffRequest, key);
            fileUploader.uploadFiles(kickoffRequest, key);
            fileUploader.assignUser(kickoffRequest, key);
        }
        fileUploader.setIssueStatus(nextState);
        fileUploader.changeStatus(transitionName, key);
    }

    @Override
    public boolean validateBefore(KickoffRequest kickoffRequest, FileUploader fileUploader, String key) {
        return validateRequest(kickoffRequest);
    }

    @Override
    public void validateAfter(String key, String summary, FileUploader fileUploader) {
        throw new IllegalStateException(String.format("Files cannot be validated in state: %s. They haven't been " +
                "generated yet.", getName()));
    }

    private boolean validateRequest(KickoffRequest request) {
        return request != null;
    }

    @Override
    public String getName() {
        return name;
    }
}
