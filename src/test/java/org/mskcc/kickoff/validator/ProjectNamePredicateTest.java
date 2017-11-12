package org.mskcc.kickoff.validator;

import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProjectNamePredicateTest {
    @Test
    public void whenProjectIsSampleSetAndNameIsValid_shouldReturnTrue() {
        Predicate<String> sampleSetPredicate = s -> true;
        Predicate<String> sampleSetNamePredicate = s -> true;
        Predicate<String> singleReqNamePredicate = s -> false;

        ProjectNamePredicate projectNamePredicate = new ProjectNamePredicate(sampleSetPredicate, sampleSetNamePredicate, singleReqNamePredicate);

        boolean isValid = projectNamePredicate.test("whatever");

        assertTrue(isValid);
    }

    @Test
    public void whenProjectIsSampleSetAndNameIsInvalid_shouldReturnFalse() {
        Predicate<String> sampleSetPredicate = s -> true;
        Predicate<String> sampleSetNamePredicate = s -> false;
        Predicate<String> singleReqNamePredicate = s -> false;

        ProjectNamePredicate projectNamePredicate = new ProjectNamePredicate(sampleSetPredicate, sampleSetNamePredicate, singleReqNamePredicate);

        boolean isValid = projectNamePredicate.test("whatever");

        assertFalse(isValid);
    }

    @Test
    public void whenProjecIsSingleRequestAndNameIsValid_shouldReturnTrue() {
        Predicate<String> sampleSetPredicate = s -> false;
        Predicate<String> sampleSetNamePredicate = s -> true;
        Predicate<String> singleReqNamePredicate = s -> true;

        ProjectNamePredicate projectNamePredicate = new ProjectNamePredicate(sampleSetPredicate, sampleSetNamePredicate, singleReqNamePredicate);

        boolean isValid = projectNamePredicate.test("whatever");

        assertTrue(isValid);
    }

    @Test
    public void whenProjectIsSingleRequestAndNameIsInvalid_shouldReturnFalse() {
        Predicate<String> sampleSetPredicate = s -> false;
        Predicate<String> sampleSetNamePredicate = s -> true;
        Predicate<String> singleReqNamePredicate = s -> false;

        ProjectNamePredicate projectNamePredicate = new ProjectNamePredicate(sampleSetPredicate, sampleSetNamePredicate, singleReqNamePredicate);

        boolean isValid = projectNamePredicate.test("whatever");

        assertFalse(isValid);
    }

}