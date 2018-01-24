package org.mskcc.kickoff.upload.jira.domain;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class JiraGroup {
    @JsonProperty("values")
    private List<JiraUser> values;

    public JiraGroup() {
    }

    public List<JiraUser> getValues() {
        return values;
    }

    public void setValues(List<JiraUser> values) {
        this.values = values;
    }
}
