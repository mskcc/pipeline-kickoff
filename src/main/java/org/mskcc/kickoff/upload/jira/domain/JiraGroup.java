package org.mskcc.kickoff.upload.jira.domain;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class JiraGroup {
    private int total;

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

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }
}
