package org.mskcc.kickoff.validator;

import org.junit.Test;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.typeCompatibleWith;

public class LimsProjectNameValidatorTest {
    private ProjectNameValidator projectNameValidator;

    @Test
    public void whenProjectNameIsValid_shouldReturnTrue() {
        projectNameValidator = new LimsProjectNameValidator(s -> true);

        assertThat(projectNameValidator.isValid("validProjectName"), is(true));
    }

    @Test
    public void whenProjectNameNotValid_shouldThrowAnException() {
        projectNameValidator = new LimsProjectNameValidator(s -> false);

        Optional<Exception> exception = assertThrown(() -> projectNameValidator.isValid("notValidProjectName"));
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(LimsProjectNameValidator.InvalidProjectNameException.class));
    }

    private Optional<Exception> assertThrown(Runnable runnable) {
        try {
            runnable.run();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(e);
        }
    }
}