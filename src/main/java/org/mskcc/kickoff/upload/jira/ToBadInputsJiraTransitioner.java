package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.upload.jira.state.BadInputsJiraIssueState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ToBadInputsJiraTransitioner implements JiraTransitioner {
    private static final Logger LOGGER = Logger.getLogger(ToBadInputsJiraTransitioner.class);

    @Value("${jira.roslin.files.insufficient.transition}")
    private String badInputsTransition;

    @Autowired
    private BadInputsJiraIssueState badInputsJiraIssueState;

    @Override
    public void transition(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        jiraFileUploader.changeStatus(badInputsTransition, kickoffRequest);
        jiraFileUploader.setJiraIssueState(badInputsJiraIssueState);
    }
}
