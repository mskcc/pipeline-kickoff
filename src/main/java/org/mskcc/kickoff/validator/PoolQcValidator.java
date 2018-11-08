package org.mskcc.kickoff.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Pool;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.Run;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.logger.PriorityAwareLogMessage;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PoolQcValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final ErrorRepository errorRepository;
    private final List<PriorityAwareLogMessage> poolQCWarnings = new ArrayList<>();

    @Autowired
    public PoolQcValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        boolean valid = true;

        for (Pool pool : kickoffRequest.getPools().values()) {
            for (Run run : pool.getRuns().values()) {
                if (run.getPoolQcStatus() != QcStatus.PASSED) {
                    if (run.getPoolQcStatus() == QcStatus.FAILED) {
                        String message = "Skipping Run ID " + run.getId() + " because it did NOT pass Sequencing " +
                                "Analysis QC: " + run.getPoolQcStatus();
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_INFO, message));
                        continue;
                    } else if (run.getPoolQcStatus() == QcStatus.UNDER_REVIEW) {
                        String message = "Run " + run.getId() + " is still under review for pool " + pool.getIgoId
                                () + " I cannot guarantee this is DONE!";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.RUN_UNDER_REVIEW));
                        valid = false;
                    } else if (run.getPoolQcStatus() == QcStatus.REQUIRED_ADDITIONAL_READS) {
                        String message = "RunID " + run.getId() + " needed additional reads. I cannot tell yet if " +
                                "they were finished. Please check.";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.RUN_NEEDS_ADDITIONAL_READS));
                        valid = false;
                    } else if (run.getPoolQcStatus() == null) {
                        String message = "RunID " + run.getId() + " has no Sequencing QC Result field set in " +
                                "Sequencing Analysis QC Results record. Fill it in in LIMS.";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.NO_QC_FOR_RUN));
                        valid = false;
                    }
                }

                if (Objects.equals(run.getId(), Constants.NULL)) {
                    String message = "Unable to find run path or related sample ID for this sequencing run";
                    poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                    Utils.setExitLater(true);
                    kickoffRequest.setMappingIssue(true);
                    valid = false;
                }
            }
        }

        return validateSampleQcAgainstPoolQC(kickoffRequest) && valid;
    }

    private boolean validateSampleQcAgainstPoolQC(KickoffRequest kickoffRequest) {
        boolean valid = true;

        // If sample and pool have the same number of samples and runIDs, return sample (more accurate information)
        // If pool has more RunIDs than sample, then use pool's data, with whatever sample specific overlap available.
        //     write warning about it
        // If sample has more run IDs than pool, send an error out because that should never happen.
        Set<Run> runsWithSampQc = kickoffRequest
                .getSamples()
                .values()
                .stream()
                .map(s -> s.getRuns().values())
                .flatMap(Collection::stream)
                .filter(r -> r.getSampleLevelQcStatus() != null)
                .collect(Collectors.toSet());

        Set<Run> runsWithPoolQc = kickoffRequest
                .getPools()
                .values()
                .stream()
                .map(s -> s.getRuns().values())
                .flatMap(Collection::stream)
                .filter(r -> r.getPoolQcStatus() != null)
                .collect(Collectors.toSet());


        if (runsWithSampQc.size() == 0) {
            if (runsWithPoolQc.size() != 0 & !kickoffRequest.isManualDemux()) {
                Utils.setExitLater(true);
                String message = "For this project the sample specific QC is not present. My script is looking at the" +
                        " pool specific QC instead, which assumes that if the pool passed, every sample in the pool " +
                        "also passed. This is not necessarily true and you might want to check the delivery email to " +
                        "make sure it includes every sample name that is on the manifest. This doesn’t mean you can’t" +
                        " put the project through, it just means that the script doesn’t know if the samples failed " +
                        "sequencing QC.";
                PM_LOGGER.log(Level.ERROR, message);
                DEV_LOGGER.log(Level.ERROR, message);
                kickoffRequest.setMappingIssue(true);
                errorRepository.add(new GenerationError("No sample specific QC present", ErrorCode.NO_SAMPLE_QC));
                valid = false;
            }
            printPoolQCWarnings();
        } else if (poolQCWarnings.stream().anyMatch(w -> w.getPriority() == PmLogPriority.POOL_ERROR)) {
            printPoolQCWarnings();
        }

        Set<Run> runsWithNonUnderReviewPoolQc = runsWithPoolQc.stream().filter(r -> r.getPoolQcStatus() != QcStatus
                .UNDER_REVIEW).collect(Collectors.toSet());
        runsWithNonUnderReviewPoolQc.removeAll(runsWithSampQc);

        if (!runsWithNonUnderReviewPoolQc.isEmpty()) {
            String message = "Sample specific QC is missing run(s) that pool QC contains. Will add POOL QC data for " +
                    "missing run(s): " + StringUtils.join(runsWithNonUnderReviewPoolQc, ", ");
            PM_LOGGER.log(PmLogPriority.SAMPLE_INFO, message);
            DEV_LOGGER.log(Level.INFO, message);
        }

        return valid;
    }

    private void printPoolQCWarnings() {
        for (PriorityAwareLogMessage poolQCWarning : poolQCWarnings) {
            PM_LOGGER.log(poolQCWarning.getPriority(), poolQCWarning.getMessage());
            if (poolQCWarning.getPriority() == PmLogPriority.POOL_ERROR)
                DEV_LOGGER.error(poolQCWarning.getMessage());
            else
                DEV_LOGGER.info(poolQCWarning.getMessage());
        }
    }
}
