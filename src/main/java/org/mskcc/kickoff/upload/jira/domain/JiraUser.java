package org.mskcc.kickoff.upload.jira.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JiraUser {
    @JsonProperty("name")
    private String userName;

    private String key;
    private String emailAddress;

    @JsonIgnore
    private String igoFormattedName;

    public JiraUser() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public void setEmailAddress(String emailAddress) {
        this.emailAddress = emailAddress;
    }

    public String getIgoFormattedName() {
        return igoFormattedName;
    }

    public void setIgoFormattedName(String igoFormattedName) {
        this.igoFormattedName = igoFormattedName;
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", key, userName);
    }
}
