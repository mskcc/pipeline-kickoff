package org.mskcc.kickoff.validator;

import java.util.function.Predicate;

public class LimsProjectNameValidator implements ProjectNameValidator {
    private final Predicate<String> projectNamePredicate;

    public LimsProjectNameValidator(Predicate<String> projectNamePredicate) {
        this.projectNamePredicate = projectNamePredicate;
    }

    @Override
    public void validate(String projectId) {
        if (!projectNamePredicate.test(projectId))
            throw new InvalidProjectNameException(String.format("Malformed request ID: %s", projectId));
    }

    static class InvalidProjectNameException extends RuntimeException {
        public InvalidProjectNameException(String message) {
            super(message);
        }
    }
}
