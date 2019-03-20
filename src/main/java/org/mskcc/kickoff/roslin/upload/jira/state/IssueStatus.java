package org.mskcc.kickoff.roslin.upload.jira.state;

import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.roslin.upload.FileUploader;

public interface IssueStatus {
    void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String key, String summary);

    boolean validateBefore(KickoffRequest kickoffRequest, FileUploader fileUploader, String key);

    void validateAfter(String key, String summary, FileUploader fileUploader);

    String getName();
}
