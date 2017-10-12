package org.mskcc.kickoff.validator;

import org.mskcc.domain.Pairedness;

import java.util.Set;
import java.util.function.Predicate;

public class PairednessValidPredicate implements Predicate<Set<Pairedness>> {
    @Override
    public boolean test(Set<Pairedness> pairednesses) {
        return pairednesses.size() == 1;
    }
}
