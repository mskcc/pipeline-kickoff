package org.mskcc.kickoff.upload.jira.state;

import java.util.HashMap;
import java.util.Map;

public class StatusFactory {
    private final Map<String, IssueStatus> nameToJiraState = new HashMap<>();

    public StatusFactory(IssueStatus... issueStatuses) {
        for (IssueStatus issueStatus : issueStatuses) {
            nameToJiraState.put(issueStatus.getName(), issueStatus);
        }
    }

    public IssueStatus getStatus(String name) {
        if (!nameToJiraState.containsKey(name))
            throw new IllegalArgumentException(String.format("No status found with name: %s", name));
        return nameToJiraState.get(name);
    }
}
