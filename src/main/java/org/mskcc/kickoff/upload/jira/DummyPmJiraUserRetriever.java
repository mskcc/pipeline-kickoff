package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.upload.jira.domain.JiraUser;
import org.mskcc.kickoff.util.Constants;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({Constants.DEV_PROFILE, Constants.TEST_PROFILE})
public class DummyPmJiraUserRetriever implements PmJiraUserRetriever {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public JiraUser retrieve(String projectManagerIgoName) {
        JiraUser jiraUser = new JiraUser();
        jiraUser.setKey("rezae");
        jiraUser.setUserName("rezae");
        jiraUser.setEmailAddress("rezae@mskcc.org");

        LOGGER.info(String.format("Using dev jira user %s", jiraUser));

        return jiraUser;
    }
}
