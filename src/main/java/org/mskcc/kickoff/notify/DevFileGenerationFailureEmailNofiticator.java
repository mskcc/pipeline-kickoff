package org.mskcc.kickoff.notify;

import org.mskcc.util.Constants;
import org.mskcc.util.email.EmailConfiguration;
import org.mskcc.util.email.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile({Constants.DEV_PROFILE, Constants.TEST_PROFILE})
@Component
public class DevFileGenerationFailureEmailNofiticator extends FileGenerationFailureEmailNofiticator {
    @Autowired
    public DevFileGenerationFailureEmailNofiticator(EmailSender emailSender, EmailConfiguration emailConfiguration) {
        super(emailSender, emailConfiguration);
    }

    @Override
    protected String getFooter() {
        return "Bazingaaaa!!!";
    }

    @Override
    protected String getTitle(String requestId) {
        return String.format("*** TEST *** \n%s", super.getTitle(requestId));
    }

    @Override
    public String getSubject(String requestId) {
        return String.format("*** TEST *** %s", super.getSubject(requestId));
    }
}
