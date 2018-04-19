package org.mskcc.kickoff.validator;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.printer.ErrorCode;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class MaxSamplesValidatorTest {
    private ErrorRepository errorRepository;
    private MaxSamplesValidator maxSamplesValidator;

    @Before
    public void setUp() throws Exception {
        errorRepository = new InMemoryErrorRepository();
        maxSamplesValidator = new MaxSamplesValidator(errorRepository);
    }

    @Test
    public void whenNumberOfSamplesIsLessThanMax_shouldNotGenerateNumberOfSamplesExceededError() throws Exception {
        assertNoOverflowError(0);
        assertNoOverflowError(1);
        assertNoOverflowError(RequestValidator.MAX_NUMBER_OF_SAMPLES - 2);
        assertNoOverflowError(RequestValidator.MAX_NUMBER_OF_SAMPLES - 1);
        assertNoOverflowError(RequestValidator.MAX_NUMBER_OF_SAMPLES);
    }

    @Test
    public void whenNumberOfSamplesIsMaxAndBigger_shouldNotGenerateNumberOfSamplesExceededError() throws Exception {
        assertOverflowError(RequestValidator.MAX_NUMBER_OF_SAMPLES + 1);

        setUp();
        assertOverflowError(RequestValidator.MAX_NUMBER_OF_SAMPLES + RequestValidator.MAX_NUMBER_OF_SAMPLES);

        setUp();
        assertOverflowError(RequestValidator.MAX_NUMBER_OF_SAMPLES * 100);
    }

    private void assertOverflowError(int numberOfSamples) {
        List<Sample> samples = getSamples(numberOfSamples);

        //when
        maxSamplesValidator.validate(samples);

        //then
        assertThat(errorRepository.getErrors().size(), is(1));
        assertThat(errorRepository.getErrors().get(0).getErrorCode(), is(ErrorCode.MAX_NUMBER_SAMPLES_EXCEEDED));
    }

    private void assertNoOverflowError(int numberOfSamples) {
        //given
        List<Sample> samples = getSamples(numberOfSamples);

        //when
        maxSamplesValidator.validate(samples);

        //then
        assertThat(errorRepository.getErrors().size(), is(0));
    }

    private List<Sample> getSamples(int numberOfSamples) {
        List<Sample> samples = new ArrayList<>();
        for (int i = 0; i < numberOfSamples; i++) {
            samples.add(new Sample(String.valueOf(i)));
        }

        return samples;
    }

}