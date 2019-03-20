package org.mskcc.kickoff.bic.domain.sample;

import java.util.function.Predicate;

class ValidSamplePredicate implements Predicate<Sample> {
    @Override
    public boolean test(Sample sample) {
        return sample.getRuns().values().stream()
                .anyMatch(r -> r.isPassed());
    }
}
