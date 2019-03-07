package org.mskcc.kickoff.upload.jira.state;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.FileUploader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HoldIssueStatus implements IssueStatus {
    @Value("${jira.roslin.hold.status}")
    private String name;

    @Override
    public void uploadFiles(KickoffRequest kickoffRequest, FileUploader fileUploader, String key, String summary) {
        throw new IllegalStateException(String.format("Files cannot be generated in state: %s", name));
    }

    @Override
    public boolean validateBefore(KickoffRequest kickoffRequest, FileUploader fileUploader, String key) {
        return true;
    }

    @Override
    public void validateAfter(String key, String summary, FileUploader fileUploader) {

    }

    @Override
    public String getName() {
        return name;
    }
}
