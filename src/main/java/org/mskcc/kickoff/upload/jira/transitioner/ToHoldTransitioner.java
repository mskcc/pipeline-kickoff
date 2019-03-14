package org.mskcc.kickoff.upload.jira.transitioner;

import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.state.HoldIssueStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("hold")
public class ToHoldTransitioner implements Transitioner {
    @Value("${jira.roslin.hold.transition}")
    private String holdTransition;

    @Autowired
    private HoldIssueStatus holdIssueStatus;

    @Override
    public void transition(FileUploader fileUploader, String key) {
        fileUploader.changeStatus(holdTransition, key);
        fileUploader.setIssueStatus(holdIssueStatus);
    }
}
