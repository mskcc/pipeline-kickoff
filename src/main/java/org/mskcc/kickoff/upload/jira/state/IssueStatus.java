package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;

public interface IssueStatus {
    void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String key, String summary);

    boolean validateBefore(KickoffRequest kickoffRequest, FileUploader fileUploader, String key);

    void validateAfter(String key, String summary, FileUploader fileUploader);

    String getName();
}
