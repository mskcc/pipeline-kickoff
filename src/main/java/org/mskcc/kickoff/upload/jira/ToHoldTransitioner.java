package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.state.HoldIssueStatus;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(Constants.HOLD_PROFILE)
@Component
public class ToHoldTransitioner implements Transitioner {
    private static final Logger LOGGER = Logger.getLogger(ToHoldTransitioner.class);

    @Value("${jira.roslin.hold.transition}")
    private String holdTransition;

    @Autowired
    private HoldIssueStatus holdJiraIssueState;

    @Override
    public void transition(FileUploader fileUploader, String issueId) {
        fileUploader.changeStatus(holdTransition, issueId);
        fileUploader.setIssueStatus(holdJiraIssueState);
    }
}
