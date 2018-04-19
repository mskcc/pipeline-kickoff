package org.mskcc.kickoff.notify;

import org.mskcc.util.Constants;
import org.mskcc.util.email.EmailConfiguration;
import org.mskcc.util.email.EmailNotificator;
import org.mskcc.util.email.EmailSender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile(Constants.PROD_PROFILE)
@Component
public class FileGenerationFailureEmailNofiticator extends EmailNotificator {
    @Autowired
    public FileGenerationFailureEmailNofiticator(EmailSender emailSender, EmailConfiguration emailConfiguration) {
        super(emailSender, emailConfiguration);
    }

    @Override
    protected String getFooter() {
        return "";
    }

    @Override
    protected String getTitle(String requestId) {
        return String.format("Hello, \n\nmanifest file(s) errors for request %s:", requestId);
    }

    @Override
    public String getSubject(String requestId) {
        return String.format("Manifest files generation errors for request: %s", requestId);
    }
}
