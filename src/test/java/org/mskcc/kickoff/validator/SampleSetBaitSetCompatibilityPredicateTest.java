package org.mskcc.kickoff.validator;

import org.junit.Test;
import org.junit.jupiter.api.Test;
import org.mskcc.domain.Recipe;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class SampleSetBaitSetCompatibilityPredicateTest {
    private SampleSetBaitSetCompatibilityPredicate sampleSetBaitSetCompatibilityPredicate = new
            SampleSetBaitSetCompatibilityPredicate();

    @Test
    public void whenBaitSetIsSame_shouldBeCompatible() throws Exception {
        String baitSet = Recipe.IMPACT_341.getValue();
        boolean isCompatible = sampleSetBaitSetCompatibilityPredicate.test(baitSet, baitSet);

        assertThat(isCompatible, is(true));
    }

}