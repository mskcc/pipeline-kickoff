package org.mskcc.kickoff.validator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class SamplesValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private ErrorRepository errorRepository;

    @Autowired
    public SamplesValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        try {
            kickoffRequest.validateHasSamples();
            return validateSamplesExist(kickoffRequest);
        } catch (KickoffRequest.NoValidSamplesException e) {
            errorRepository.add(new GenerationError(e.getMessage(), ErrorCode.NO_VALID_SAMPLES));
            throw e;
        }
    }

    public boolean validateSamplesExist(KickoffRequest kickoffRequest) {
        int numberOfValidNonNormalSamples = kickoffRequest.getValidNonPooledNormalSamples().size();
        if (numberOfValidNonNormalSamples == 0) {
            Utils.setExitLater(true);
            String message = "None of the samples in the request were found in the passing samples and runs. Please " +
                    "check the LIMs to see if the names are incorrect.";
            PM_LOGGER.log(Level.ERROR, message);
            DEV_LOGGER.log(Level.ERROR, message);
            errorRepository.add(new GenerationError(message, ErrorCode.NO_PASSING_SAMPLES));
            return false;
        }

        if (kickoffRequest.getSamples().size() == 0) {
            String message = String.format("No samples found for request: %s", kickoffRequest.getId());
            errorRepository.add(new GenerationError(message, ErrorCode.NO_SAMPLES));
            throw new RuntimeException(message);
        }

        return true;
    }
}
