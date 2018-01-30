package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.jira.state.HoldJiraIssueState;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(Constants.HOLD_PROFILE)
@Component
public class ToHoldJiraTransitioner implements JiraTransitioner {
    private static final Logger LOGGER = Logger.getLogger(ToHoldJiraTransitioner.class);

    @Value("${jira.roslin.hold.transition}")
    private String holdTransition;

    @Autowired
    private HoldJiraIssueState holdJiraIssueState;

    @Override
    public void transition(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        jiraFileUploader.changeStatus(holdTransition, kickoffRequest);
        jiraFileUploader.setJiraIssueState(holdJiraIssueState);
    }
}
