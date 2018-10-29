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
    private String defaultCmoPmKey = "bourquec";
    private String defaultIgoPmKey = "cobbsc";
    private String currentValidIgoName = "Selcuklu, S. Duygu";

    @Lazy
    @Autowired
    private PmJiraUserRetriever pmJiraUserRetriever;

    @Before
    public void setup() {
        FromJiraPmJiraUserRetrieverTestConfig.setJiraPassword(jiraPassword);
        FromJiraPmJiraUserRetrieverTestConfig.setJiraUsername(jiraUsername);
    }

    @Test
    public void whenPMangerNameIsValidAndIsInIgoPmGroup_shouldReturnMatchedJiraUser() {
        JiraUser assignee = pmJiraUserRetriever.retrieve(currentValidIgoName);
        assertThat(assignee.getKey(), is("selcukls"));
    }

    @Test
    public void whenPMangerNameIsValidAndIsInNotIgoPmGroup_shouldReturnDefaultCmoPM() {
        JiraUser assignee = pmJiraUserRetriever.retrieve("Liu, Feng");
        assertThat(assignee.getKey(), is(defaultCmoPmKey));
    }

    @Test
    public void whenPMangerNameIsInValid_shouldReturnDefaultCmoPM() {
        JiraUser assignee = pmJiraUserRetriever.retrieve("");
        assertThat(assignee.getKey(), is(defaultCmoPmKey));
    }

    @Test
    public void whenCmoSideProject_shouldReturnDefaultIgoPm() {
        JiraUser assignee = pmJiraUserRetriever.retrieve(Constants.NO_PM);
        assertThat(assignee.getKey(), is(defaultIgoPmKey));
    }

}
