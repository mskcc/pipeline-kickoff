package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class PostSeqQcValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        // This will look through all post seq QC records
        // For each, if sample AND run is in map
        // then check the value of PostSeqQCStatus
        // if passing, continue
        // if failed, output WARNING that this sample failed post seq qc and WHY
        //            remove the run id

        // I'm going to add Sample ID and run ID here when I'm done yo! That way I can check and see if any
        // Don't have PostSeqAnalysisQC

        for (Sample sample : kickoffRequest.getSamples(s -> !s.isPooledNormal()).values()) {
            for (Run run : sample.getRuns().values()) {
                if (run.isSampleQcPassed()) {
                    QcStatus status = run.getPostQcStatus();
                    if (status != null && status != QcStatus.PASSED) {
                        String note = run.getNote();
                        note = note.replaceAll("\n", " ");
                        String message = String.format("Sample %s in run %s did not pass POST Sequencing QC(%s). The " +
                                        "note attached says: %s. This will not be included in manifest.",
                                sample.getCmoSampleId(), run.getId(), status, note);
                        PM_LOGGER.log(PmLogPriority.WARNING, message);
                        DEV_LOGGER.warn(message);
                    } else {
                        sample.addRun(run);
                    }
                }
            }
        }

        for (Sample sample : kickoffRequest.getSamples(s -> !s.isPooledNormal()).values()) {
            Set<Run> runsWithoutPostQc = sample.getRuns().values().stream()
                    .filter(r -> r.getPostQcStatus() == null)
                    .collect(Collectors.toSet());

            if (runsWithoutPostQc.size() > 0) {
                String message = String.format("Sample %s has runs that do not have POST Sequencing QC. We won't be " +
                                "able to tell if they are failed or not: %s. They will still be added to the sample " +
                                "list.",
                        sample.getCmoSampleId(), Arrays.toString(runsWithoutPostQc.toArray()));
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }

        return true;
    }
}
