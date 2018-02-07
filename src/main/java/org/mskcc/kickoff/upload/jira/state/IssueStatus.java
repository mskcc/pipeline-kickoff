package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;

public interface IssueStatus {
    void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader);

    void validateInputs(String issueId);

    String getName();
}
