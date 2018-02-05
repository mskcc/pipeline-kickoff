package org.mskcc.kickoff.upload.jira.state;

import java.util.HashMap;
import java.util.Map;

public class JiraStateFactory {
    private final Map<String, JiraIssueState> nameToJiraState = new HashMap<>();

    public JiraStateFactory(JiraIssueState... jiraStates) {
        for (JiraIssueState jiraState : jiraStates) {
            nameToJiraState.put(jiraState.getName(), jiraState);
        }
    }

    public JiraIssueState getJiraState(String name) {
        if (!nameToJiraState.containsKey(name))
            throw new IllegalArgumentException(String.format("No jira state found with name: %s", name));
        return nameToJiraState.get(name);
    }
}
