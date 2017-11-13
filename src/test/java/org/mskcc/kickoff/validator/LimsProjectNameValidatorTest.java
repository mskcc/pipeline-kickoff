package org.mskcc.kickoff.validator;

import org.junit.Test;
import org.mskcc.util.TestUtils;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.typeCompatibleWith;

//import org.mskcc.TestUtils;

public class LimsProjectNameValidatorTest {
    private ProjectNameValidator projectNameValidator;

    @Test
    public void whenProjectNameIsValid_shouldNotThrowException() {
        projectNameValidator = new LimsProjectNameValidator(s -> true);

        projectNameValidator.validate("validProjectName");
    }

    @Test
    public void whenProjectNameNotValid_shouldThrowAnException() {
        projectNameValidator = new LimsProjectNameValidator(s -> false);

        Optional<Exception> exception = TestUtils.assertThrown(() -> projectNameValidator.validate
                ("notValidProjectName"));
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(LimsProjectNameValidator.InvalidProjectNameException.class));
    }
}