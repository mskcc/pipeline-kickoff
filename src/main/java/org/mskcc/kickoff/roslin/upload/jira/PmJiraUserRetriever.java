package org.mskcc.kickoff.roslin.upload.jira;

import org.mskcc.kickoff.roslin.upload.jira.domain.JiraUser;

public interface PmJiraUserRetriever {
    JiraUser retrieve(String projectManagerIgoName);
}
