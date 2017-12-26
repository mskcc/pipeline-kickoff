package org.mskcc.kickoff.upload.jira;

public class JiraTransitions {
    private final String fastqsAvailableStatus;
    private final String generatedTransition;
    private final String regenerateStatus;
    private final String regeneratedTransition;

    public JiraTransitions(String fastqsAvailableStatus, String generatedTransition, String regenerateStatus, String
            regeneratedTransition) {
        this.fastqsAvailableStatus = fastqsAvailableStatus;
        this.generatedTransition = generatedTransition;
        this.regenerateStatus = regenerateStatus;
        this.regeneratedTransition = regeneratedTransition;
    }

    public String getFastqsAvailableStatus() {
        return fastqsAvailableStatus;
    }

    public String getGeneratedTransition() {
        return generatedTransition;
    }

    public String getRegenerateStatus() {
        return regenerateStatus;
    }

    public String getRegeneratedTransition() {
        return regeneratedTransition;
    }
}
