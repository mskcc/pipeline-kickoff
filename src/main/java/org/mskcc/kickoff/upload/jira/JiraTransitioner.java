package org.mskcc.kickoff.upload.jira;

import org.mskcc.kickoff.domain.KickoffRequest;

public interface JiraTransitioner {
    void transition(KickoffRequest kickoffRequest, JiraFileUploader jiraFileUploader);
}
