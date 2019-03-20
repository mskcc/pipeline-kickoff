package org.mskcc.kickoff.bic.validator;

import org.mskcc.kickoff.config.Arguments;

import java.util.function.Predicate;

public class LimsProjectNameValidator implements ProjectNameValidator {
    private Predicate<String> projectNamePredicate;

    public LimsProjectNameValidator(Predicate<String> projectNamePredicate) {
        this.projectNamePredicate = projectNamePredicate;
    }

    @Override
    public boolean isValid(String project) {
        if (!projectNamePredicate.test(project)) {
            throw new InvalidProjectNameException(String.format("Malformed request ID: %s", project));
        }
        return true;
    }

    static class InvalidProjectNameException extends RuntimeException {
        public InvalidProjectNameException(String message) {
            super(message);
        }
    }
}
