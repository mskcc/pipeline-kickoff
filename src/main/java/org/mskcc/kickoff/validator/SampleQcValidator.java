package org.mskcc.kickoff.validator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component
class SampleQcValidator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final ErrorRepository errorRepository;

    @Autowired
    public SampleQcValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    public void validate(KickoffRequest kickoffRequest) {
        Collection<Sample> nonPooledNormalUniqueSamples = kickoffRequest.getNonPooledNormalUniqueSamples
                (Sample::getCmoSampleId);

        for (Sample sample : nonPooledNormalUniqueSamples) {
            Collection<Run> runs = sample.getRuns().values();
            for (Run run : runs) {
                if (run.getSampleLevelQcStatus() != null && run.getSampleLevelQcStatus() != QcStatus.PASSED) {
                    if (run.getSampleLevelQcStatus() == QcStatus.FAILED) {
                        String message = String.format("Not including Sample %s from Run ID %s because it did NOT " +
                                "pass Sequencing Analysis QC: %s", sample, run.getId(), run.getSampleLevelQcStatus());
                        PM_LOGGER.log(PmLogPriority.SAMPLE_INFO, message);
                        DEV_LOGGER.log(Level.INFO, message);
                    } else if (run.getSampleLevelQcStatus() == QcStatus.UNDER_REVIEW) {
                        String message = String.format("Sample %s from run id %s is still under review. It need to be" +
                                " either failed or passed", sample, run.getId());
                        Utils.setExitLater(true);
                        PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.SAMPLES_UNDER_REVIEW));
                    } else {
                        String message = String.format("Sample %s from run id %s needed additional reads.", sample,
                                run);
                        Utils.setExitLater(true);
                        PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.SAMPLE_NEEDS_ADDITIONAL_READS));
                    }
                }
            }
            if (sample.getAlias() != null && !sample.getAlias().isEmpty()) {
                String message = "SAMPLE " + sample.getIgoId() + " HAS AN ALIAS: " + sample.getAlias() + "!";
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }
    }
}
