package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.state.BadInputsIssueStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ToBadInputsTransitioner implements Transitioner {
    private static final Logger LOGGER = Logger.getLogger(ToBadInputsTransitioner.class);

    @Value("${jira.roslin.files.insufficient.transition}")
    private String badInputsTransition;

    @Autowired
    private BadInputsIssueStatus badInputsIssueStatus;

    @Override
    public void transition(FileUploader fileUploader, String issueId) {
        fileUploader.changeStatus(badInputsTransition, issueId);
        fileUploader.setIssueStatus(badInputsIssueStatus);
    }
}
