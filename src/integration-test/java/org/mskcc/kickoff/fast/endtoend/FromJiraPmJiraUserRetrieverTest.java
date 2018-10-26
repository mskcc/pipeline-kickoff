package org.mskcc.kickoff.fast.endtoend;

import org.apache.commons.codec.binary.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mskcc.kickoff.upload.jira.PmJiraUserRetriever;
import org.mskcc.kickoff.upload.jira.domain.JiraGroup;
import org.mskcc.kickoff.upload.jira.domain.JiraUser;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles({"test", "tango"})
@ContextConfiguration(classes = FromJiraPmJiraUserRetrieverTestConfig.class)
public class FromJiraPmJiraUserRetrieverTest {

    @Value("${jira.username}")
    private String jiraUsername;

    @Value("${jira.password}")
    private String jiraPassword;

    @Value("${jira.url}")
    private String jiraUrl;

    @Value("${jira.pm.group.name}")
    private String pmGroupName;

    @Value("${jira.rest.path}")
    private String jiraRestPath;

    // will need change later if PM left or removed from PM group at jira
    private String currentValidIgoName = "Bourque, Caitlin";

    @Lazy
    @Autowired
    private PmJiraUserRetriever pmJiraUserRetriever;

    @Before
    public void setup() {
        FromJiraPmJiraUserRetrieverTestConfig.setJiraPassword(jiraPassword);
        FromJiraPmJiraUserRetrieverTestConfig.setJiraUsername(jiraUsername);
    }

    @Test
    public void whenPMangerNameIsValid_shouldReturnMatchedJiraUser() {
        JiraUser assignee = pmJiraUserRetriever.retrieve(currentValidIgoName);
        assertThat(assignee.getKey(), is("bourquec"));
    }

    @Test
    public void whenNO_PM_shouldReturnMatchedFirstJiraGroupMember() {
        JiraUser assignee = pmJiraUserRetriever.retrieve(Constants.NO_PM);
        List<JiraUser> jiraPmUsers = getPmJiraUsers();
        assertThat(assignee.getKey(), is(jiraPmUsers.get(0).getKey()));
    }

    @Test
    public void whenPMangerNameIsNotFound_shouldReturnMatchedFirstJiraGroupMember() {
        JiraUser assignee = pmJiraUserRetriever.retrieve("Liu, Feng");
        List<JiraUser> jiraPmUsers = getPmJiraUsers();
        assertThat(assignee.getKey(), is(jiraPmUsers.get(0).getKey()));
    }

    private List<JiraUser> getPmJiraUsers() {
        String getAllPmUsersUrl = String.format("%s/%s/group/member?groupname=%s", jiraUrl, jiraRestPath,
                pmGroupName);
        System.out.println(getAllPmUsersUrl);

        HttpEntity<String> request = getHttpHeaders();
        ResponseEntity<JiraGroup> response = new RestTemplate().exchange(getAllPmUsersUrl, HttpMethod.GET, request,
                JiraGroup.class);
        List<JiraUser> users = response.getBody().getValues();
        System.out.println(String.format("Found %s users in group %s:", users.size(), pmGroupName));
        return users;
    }

    private HttpEntity<String> getHttpHeaders() {
        String plainCreds = String.format("%s:%s", jiraUsername, jiraPassword);
        byte[] plainCredsBytes = plainCreds.getBytes();
        byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
        String base64Creds = new String(base64CredsBytes);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Basic " + base64Creds);
        return new HttpEntity<>(headers);
    }

}
