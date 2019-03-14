package org.mskcc.kickoff.upload.jira.transitioner;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.upload.FileUploader;
import org.mskcc.kickoff.upload.jira.transitioner.ToHoldTransitioner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!hold")
public class DummyTransitioner extends ToHoldTransitioner {

    private static final Logger LOGGER = Logger.getLogger(DummyTransitioner.class);

    @Override
    public void transition(FileUploader fileUploader, String key) {
        // do not do any transition in NOT "hold" profile
        LOGGER.info("Do dummy transition for issue " + key);
    }
}
