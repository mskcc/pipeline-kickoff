package org.mskcc.kickoff.pairing;

import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.util.Constants;

import java.util.function.BiPredicate;

public class SampleSetPairingInfoValidPredicate implements BiPredicate<Sample, Sample> {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private BiPredicate<Sample, Sample> pairingInfoValidPredicate;
    private BiPredicate<String, String> baitSetValidPredicate;

    public SampleSetPairingInfoValidPredicate(
            BiPredicate<Sample, Sample> pairingInfoValidPredicate,
            BiPredicate<String, String> baitSetValidPredicate) {
        this.pairingInfoValidPredicate = pairingInfoValidPredicate;
        this.baitSetValidPredicate = baitSetValidPredicate;
    }

    @Override
    public boolean test(Sample sample, Sample sample2) {
        boolean isValid = pairingInfoValidPredicate.test(sample, sample2) && baitSetValidPredicate.test(sample.get
                (Constants
                        .BAIT_VERSION), sample2.get(Constants.BAIT_VERSION));

        if (!isValid)
            LOGGER.warn(String.format("Pairing between sample %s and sample %s is not valid", sample.getIgoId(),
                    sample2.getIgoId()));

        return isValid;
    }
}
