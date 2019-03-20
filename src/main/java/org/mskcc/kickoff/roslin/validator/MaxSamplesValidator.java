package org.mskcc.kickoff.roslin.validator;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.roslin.notify.GenerationError;
import org.mskcc.kickoff.roslin.printer.ErrorCode;

import java.util.List;

public class MaxSamplesValidator {
    static final int MAX_NUMBER_OF_SAMPLES = 200;
    private ErrorRepository errorRepository;

    public MaxSamplesValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    public void validate(List<Sample> samples) {
        int numberOfSamples = samples.size();
        if (numberOfSamples > MAX_NUMBER_OF_SAMPLES) {
            String message = String.format("Max number of samples exceeded. Current: %d, max: %d", numberOfSamples,
                    MAX_NUMBER_OF_SAMPLES);
            errorRepository.add(new GenerationError(message, ErrorCode.MAX_NUMBER_SAMPLES_EXCEEDED));
        }
    }
}
