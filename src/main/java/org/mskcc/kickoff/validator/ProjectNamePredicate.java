package org.mskcc.kickoff.validator;

import java.util.function.Predicate;

public class ProjectNamePredicate implements Predicate<String> {
    private static final String PROJECT_NAME_REGEX = "^[0-9]{5,}[A-Z_]*$";

    @Override
    public boolean test(String projectName) {
        return !(projectName == null || !projectName.matches(PROJECT_NAME_REGEX));
    }
}
