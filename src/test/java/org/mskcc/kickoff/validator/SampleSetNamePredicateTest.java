package org.mskcc.kickoff.validator;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SampleSetNamePredicateTest {
    private SampleSetNamePredicate sampleSetNamePredicate = new SampleSetNamePredicate();

    @Test
    public void whenSampleSetNameStartsWithLowerCaseSet_shouldBeValid() {
        boolean isValid = sampleSetNamePredicate.test("set_whatever");

        assertTrue(isValid);
    }

    @Test
    public void whenSampleSetNameStartsWithUpperCaseSet_shouldBeValid() {
        boolean isValid = sampleSetNamePredicate.test("Set_whatever");

        assertTrue(isValid);
    }

    @Test
    public void whenSampleSetNameDoesntStartWithSet_shouldBeInvalid() {
        boolean isValid = sampleSetNamePredicate.test("someting_different_that_set_whatever");

        assertFalse(isValid);
    }
}