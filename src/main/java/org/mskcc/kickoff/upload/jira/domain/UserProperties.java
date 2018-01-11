package org.mskcc.kickoff.upload.jira.domain;

import java.util.List;

public class UserProperties {
    private List<JiraUserProperty> keys;

    public List<JiraUserProperty> getKeys() {
        return keys;
    }

    public void setKeys(List<JiraUserProperty> keys) {
        this.keys = keys;
    }
}
