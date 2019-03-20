package org.mskcc.kickoff.roslin.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.SpringProfile;
import org.mskcc.kickoff.roslin.upload.jira.domain.JiraUser;
import org.mskcc.kickoff.roslin.util.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({SpringProfile.DEV, SpringProfile.TEST})
public class DummyPmJiraUserRetriever implements PmJiraUserRetriever {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Value("${jira.dev.pm.key}")
    private String key;

    @Value("${jira.dev.pm.username}")
    private String username;

    @Value("${jira.dev.pm.email}")
    private String email;

    @Override
    public JiraUser retrieve(String projectManagerIgoName) {
        JiraUser jiraUser = new JiraUser();
        jiraUser.setKey(key);
        jiraUser.setUserName(username);
        jiraUser.setEmailAddress(email);

        LOGGER.info(String.format("Using dev jira user %s", jiraUser));

        return jiraUser;
    }
}
