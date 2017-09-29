package org.mskcc.kickoff.validator;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class SingleRequestNamePredicateTest {
    private SingleRequestNamePredicate singleRequestNamePredicate = new SingleRequestNamePredicate();

    @Test
    public void whenProjectIsNameNull_shouldReturnFalse() {
        assertProjectName(null, false);
    }

    @Test
    public void whenProjectNameIsEmpty_shouldReturnFalse() {
        assertProjectName("", false);
    }

    @Test
    public void whenProjectNameHasTooFewDigits_shouldReturnFalse() {
        assertProjectName("1", false);
        assertProjectName("13", false);
        assertProjectName("613", false);
        assertProjectName("1234", false);
    }

    @Test
    public void whenProjectNameDoesNotStartWithDigit_shouldReturnFalse() {
        assertProjectName("A1234", false);
        assertProjectName("ADGHS_A", false);
    }

    @Test
    public void whenProjectNameDoesNotContainOnlyDigitsInFirstPart_shouldReturnFalse() {
        assertProjectName("12A34", false);
        assertProjectName("12435A1", false);
    }

    @Test
    public void whenProjectNameContainsNotAllowedSymbols_shouldReturnFalse() {
        assertProjectName("12345_$", false);
        assertProjectName("12345-A", false);
        assertProjectName("_32124_AB", false);
        assertProjectName("9567487(A)", false);
    }

    @Test
    public void whenProjectNameHasLetterAndEndsWithDigit_shouldReturnFalse() {
        assertProjectName("12345_A7", false);
    }

    @Test
    public void whenProjectNameContainsLowerCaseLetters_shouldReturnFalse() {
        assertProjectName("12345_a", false);
    }

    @Test
    public void whenProjectNameDoesContainFiveDigits_shouldReturnTrue() {
        assertProjectName("68352", true);
    }

    @Test
    public void whenProjectNameDoesContainFiveDigitsAndLetters_shouldReturnTrue() {
        assertProjectName("4728903ABC", true);
    }

    @Test
    public void whenProjectNameDoesContainFiveDigitsLettersAndUnderscore_shouldReturnTrue() {
        assertProjectName("164723_R", true);
    }

    @Test
    public void whenProjectNameDoesContainFiveDigitsLettersEndsWithUnderscore_shouldReturnTrue() {
        assertProjectName("164723_R_T_D_", true);
    }

    @Test
    public void whenProjectNameDoesContainManyDigits_shouldReturnTrue() {
        assertProjectName("68352767867867867868647382964732894672389647238", true);
    }

    private void assertProjectName(String projectName, boolean isValid) {
        boolean projectNameValid = singleRequestNamePredicate.test(projectName);
        assertThat(projectNameValid, is(isValid));
    }
}