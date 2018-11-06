package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.function.Predicate;

@Component
public class ReadCountsValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        // For each sample in readsForSample if the number of reads is less than 1M, print out a warning
        for (Sample sample : getSamplesWithSampleQc(kickoffRequest)) {
            long numberOfReads = sample.getNumberOfReads();
            if (numberOfReads < Constants.MINIMUM_NUMBER_OF_READS) {
                // Print AND add to readme file
                String extraReadmeInfo = String.format("\n[WARNING] sample %s has less than 1M total reads: %d\n",
                        sample, numberOfReads);
                kickoffRequest.setExtraReadMeInfo(kickoffRequest.getExtraReadMeInfo() + extraReadmeInfo);
                String message = String.format("sample %s has less than 1M total reads: %d", sample, numberOfReads);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }

        return true;
    }

    private Collection<Sample> getSamplesWithSampleQc(KickoffRequest kickoffRequest) {
        return kickoffRequest.getSamples(s -> s.getRuns().values().stream().anyMatch(r -> r.getSampleLevelQcStatus()
                == QcStatus.PASSED)).values();
    }
}
