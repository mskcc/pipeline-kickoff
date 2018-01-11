package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!" + Constants.HOLD_PROFILE)
@Component
public class DummyJiraTransitioner implements JiraTransitioner {
    @Override
    public void transition(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader) {
        // do not do any transition in NOT "hold" profile
    }
}
