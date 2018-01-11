package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.upload.jira.domain.JiraUser;

public interface PmJiraUserRetriever {
    JiraUser retrieve(String projectManagerIgoName);
}
