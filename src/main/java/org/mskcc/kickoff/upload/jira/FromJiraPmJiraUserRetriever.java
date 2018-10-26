package org.mskcc.kickoff.upload.jira;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.upload.jira.domain.JiraGroup;
import org.mskcc.kickoff.upload.jira.domain.JiraUser;
import org.mskcc.kickoff.upload.jira.domain.JiraUserProperty;
import org.mskcc.kickoff.upload.jira.domain.UserProperties;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    private RestTemplate restTemplate;

    public FromJiraPmJiraUserRetriever(@Qualifier("jiraRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public JiraUser retrieve(String projectManagerIgoName) {
        LOGGER.info(String.format("Looking for jira user with IGO name [%s]", projectManagerIgoName));

        List<JiraUser> pmJiraUsers = getPmJiraUsers();

        if (isProjectManagerIgoNameValid(projectManagerIgoName)) {
            Optional<JiraUser> matchedPmJiraUser = tryMatchPmJiraUsers(projectManagerIgoName, pmJiraUsers);
            if (matchedPmJiraUser.isPresent()) return matchedPmJiraUser.get();
        }

        if (pmJiraUsers.isEmpty()) {
            throw new NoPmFoundException(String.format("No Project Manager found in jira group [%s].", pmGroupName));
        } else {
            LOGGER.info(String.format("No Project Manager in jira group [%s] matching " +
                    "IGO name [%s]: use first member returned by the Jira API.", pmGroupName, projectManagerIgoName));
            return pmJiraUsers.get(0);
        }
    }

    private boolean isProjectManagerIgoNameValid(String projectManagerIgoName) {
        if (StringUtils.isNotBlank(projectManagerIgoName)
                && !Constants.NO_PM.equalsIgnoreCase(projectManagerIgoName)
                && !Constants.NA.equals(projectManagerIgoName)){
            return true;
        }
        LOGGER.warn(String.format("Invalid Project manager igo name: [%s].", projectManagerIgoName));
        return false;
    }

    private Optional<JiraUser> tryMatchPmJiraUsers(String projectManagerIgoName, List<JiraUser> pmJiraUsers) {
        for (JiraUser pmJiraUser : pmJiraUsers) {
            if (userHasProperty(pmJiraUser, igoFormattedNameProperty)) {
                String igoFormattedName = getIgoFormattedName(pmJiraUser);
                if (Objects.equals(igoFormattedName, projectManagerIgoName)) {
                    LOGGER.info(String.format("Project Manager jira user found %s for IGO formatted name %s",
                            pmJiraUser, projectManagerIgoName));
                    return Optional.of(pmJiraUser);
                }
            }
        }

        return Optional.empty();
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
