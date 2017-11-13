package org.mskcc.kickoff.validator;

import java.util.function.Predicate;

public class ProjectNamePredicate implements Predicate<String> {
    private final Predicate<String> sampleSetProjectPredicate;
    private final Predicate<String> sampleSetNamePredicate;
    private final Predicate<String> singleRequestNamePredicate;

    public ProjectNamePredicate(Predicate<String> sampleSetProjectPredicate, Predicate<String>
            sampleSetNamePredicate, Predicate<String> singleRequestNamePredicate) {
        this.sampleSetProjectPredicate = sampleSetProjectPredicate;
        this.sampleSetNamePredicate = sampleSetNamePredicate;
        this.singleRequestNamePredicate = singleRequestNamePredicate;
    }

    @Override
    public boolean test(String projectName) {
        if (sampleSetProjectPredicate.test(projectName))
            return sampleSetNamePredicate.test(projectName);
        return singleRequestNamePredicate.test(projectName);
    }
}
