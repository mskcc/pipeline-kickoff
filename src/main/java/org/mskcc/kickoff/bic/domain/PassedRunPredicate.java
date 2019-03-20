package org.mskcc.kickoff.bic.domain;

import java.util.function.Predicate;

public class PassedRunPredicate implements Predicate<Run> {
    @Override
    public boolean test(Run run) {
        return run.isSampleQcPassed()
                && run.isPostQcPassed();
    }
}
