package org.mskcc.kickoff.velox;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SampleSetProjectPredicateTest {
    private SampleSetProjectPredicate sampleSetProjectPredicate = new SampleSetProjectPredicate();

    @Test
    public void whenProjectNameStartsWithLowerCaseSet_shouldBeTreatedAsSampleSet() {
        assertTrue(sampleSetProjectPredicate.test("set_"));
    }

    @Test
    public void whenProjectNameStartsWithUpperCaseSet_shouldBeTreatedAsSampleSet() {
        assertTrue(sampleSetProjectPredicate.test("Set_"));
    }

    @Test
    public void whenProjectNameDoesntStartWithSet_shouldNotBeTreatedAsSampleSet() {
        assertFalse(sampleSetProjectPredicate.test("not_set"));
    }

}