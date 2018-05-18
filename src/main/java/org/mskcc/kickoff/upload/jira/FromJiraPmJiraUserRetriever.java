package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.upload.jira.domain.JiraGroup;
import org.mskcc.kickoff.upload.jira.domain.JiraUser;
import org.mskcc.kickoff.upload.jira.domain.JiraUserProperty;
import org.mskcc.kickoff.upload.jira.domain.UserProperties;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

@Component
@Profile(Constants.PROD_PROFILE)
public class FromJiraPmJiraUserRetriever implements PmJiraUserRetriever {
    private static final Logger LOGGER = Logger.getLogger(org.mskcc.kickoff.util.Constants.DEV_LOGGER);

    @Value("${jira.pm.group.name}")
    private String pmGroupName;

    @Value("${jira.igo.formatted.name.property}")
    private String igoFormattedNameProperty;

    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.username}")
    private String username;

    @Value("${jira.password}")
    private String password;

    @Value("${jira.rest.path}")
    private String jiraRestPath;

    @Value("${default.pm}")
    private String defaultPm;

    @Autowired
    @Qualifier("jiraRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public JiraUser retrieve(String projectManagerIgoName) {
        projectManagerIgoName = getPmIgoName(projectManagerIgoName);

        LOGGER.info(String.format("Looking for jira user with IGO name \"%s\"", projectManagerIgoName));

        List<JiraUser> pmJiraUsers = getPmJiraUsers();
        for (JiraUser pmJiraUser : pmJiraUsers) {
            if (userHasProperty(pmJiraUser, igoFormattedNameProperty)) {
                String igoFormattedName = getIgoFormattedName(pmJiraUser);
                if (Objects.equals(igoFormattedName, projectManagerIgoName)) {
                    LOGGER.info(String.format("Project Manager jira user found %s for IGO formatted name %s",
                            pmJiraUser, projectManagerIgoName));
                    return pmJiraUser;
                }
            }
        }

        throw new NoPmFoundException(String.format("There is no Project Manager in jira group %s " +
                        "with IGO name %s",
                pmGroupName, projectManagerIgoName));
    }

    private String getPmIgoName(String projectManagerIgoName) {
        if (Constants.NO_PM.equals(projectManagerIgoName)) {
            LOGGER.warn(String.format("Project manager in LIMS is set to %s. Default PM will be used instead: %s",
                    Constants.NO_PM, defaultPm));

            projectManagerIgoName = defaultPm;
        }

        return projectManagerIgoName;
    }

    private boolean userHasProperty(JiraUser pmJiraUser, String igoFormattedNameProperty) {
        String getUserPropertiesUrl = String.format("%s/%s/user/properties?username=%s", jiraUrl, jiraRestPath,
                pmJiraUser
                        .getUserName());

        UserProperties userProperties = restTemplate.getForObject(getUserPropertiesUrl, UserProperties.class);

        return userProperties.getKeys().stream()
                .anyMatch(k -> Objects.equals(k.getKey(), igoFormattedNameProperty));
    }

    private List<JiraUser> getPmJiraUsers() {
        LOGGER.info(String.format("Getting all users for jira group %s", pmGroupName));

        String getAllPmUsersUrl = String.format("%s/%s/group/member?groupname=%s", jiraUrl, jiraRestPath,
                pmGroupName);

        JiraGroup jiraGroup = restTemplate.getForObject(getAllPmUsersUrl, JiraGroup.class);

        List<JiraUser> users = jiraGroup.getValues();
        LOGGER.info(String.format("Found %s users in group %s: %s", users.size(), pmGroupName, users));

        return users;
    }

    private String getIgoFormattedName(JiraUser pmJiraUser) {
        String getIgoFormattedNameUrl = String.format("%s/%s/user/properties/%s?userKey=%s", jiraUrl, jiraRestPath,
                igoFormattedNameProperty, pmJiraUser.getKey());

        ResponseEntity<JiraUserProperty> jiraUserPropertyResponseEntity = restTemplate.exchange
                (getIgoFormattedNameUrl, HttpMethod.GET,
                        null, JiraUserProperty.class);

        JiraUserProperty jiraUserProperty = jiraUserPropertyResponseEntity.getBody();

        return jiraUserProperty.getValue();
    }

    private class NoPmFoundException extends RuntimeException {
        public NoPmFoundException(String message) {
            super(message);
        }
    }
}
