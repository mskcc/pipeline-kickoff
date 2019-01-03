package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.upload.FileUploader;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!hold")
public class DummyTransitioner extends ToHoldTransitioner {
    @Override
    public void transition(FileUploader fileUploader, String issueId) {
        // do not do any transition in NOT "hold" profile
    }
}
