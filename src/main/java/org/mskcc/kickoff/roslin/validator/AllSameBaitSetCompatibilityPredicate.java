package org.mskcc.kickoff.roslin.validator;

import java.util.function.BiPredicate;

public class AllSameBaitSetCompatibilityPredicate implements BiPredicate<String, String> {
    @Override
    public boolean test(String baitSet1, String baitSet2) {
        return baitSet1.equals(baitSet2);
    }
}
