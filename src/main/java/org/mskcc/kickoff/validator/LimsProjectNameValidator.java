package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.util.Constants;

import java.util.function.Predicate;

public class LimsProjectNameValidator implements ProjectNameValidator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private Predicate<String> projectNamePredicate;

    public LimsProjectNameValidator(Predicate<String> projectNamePredicate) {
        this.projectNamePredicate = projectNamePredicate;
    }

    @Override
    public void validate(String project) {
        if (!projectNamePredicate.test(Arguments.project)) {
            String errorMessage = String.format("Malformed request ID: %s", Arguments.project);
            PM_LOGGER.error(errorMessage);
            throw new InvalidProjectNameException(errorMessage);
        }
    }

    static class InvalidProjectNameException extends RuntimeException {
        public InvalidProjectNameException(String message) {
            super(message);
        }
    }
}
