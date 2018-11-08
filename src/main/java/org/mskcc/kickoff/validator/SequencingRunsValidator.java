package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.function.Predicate;

import static org.mskcc.kickoff.config.Arguments.forced;

public class SequencingRunsValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private ErrorRepository errorRepository;

    @Autowired
    public SequencingRunsValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        long numberOfQcs = kickoffRequest.getSamples().values().stream()
                .flatMap(s -> s.getRuns().values().stream()
                        .filter(r -> r.getSampleLevelQcStatus() != null || r.getPoolQcStatus() != null))
                .count();

        if (numberOfQcs == 0) {
            if (!forced && !kickoffRequest.isManualDemux()) {
                String message = "No sequencing runs found for this Request ID.";
                DEV_LOGGER.error(message);
                errorRepository.add(new GenerationError(message, ErrorCode.NO_SEQ_RUNS));
                throw new RuntimeException("There are no sample level qc set.");
            } else if (kickoffRequest.isManualDemux()) {
                PM_LOGGER.log(PmLogPriority.WARNING, "MANUAL DEMULTIPLEXING was performed. Nothing but the request " +
                        "file should be output.");
                DEV_LOGGER.warn("MANUAL DEMULTIPLEXING was performed. Nothing but the request file should be output.");
            } else {
                PM_LOGGER.log(PmLogPriority.WARNING, "ALERT: There are no sequencing runs passing QC for this run. " +
                        "Force is true, I will pull ALL samples for this project.");
                DEV_LOGGER.warn("ALERT: There are no sequencing runs passing QC for this run. Force is true, I will " +
                        "pull ALL samples for this project.");
                kickoffRequest.setProcessingType(new ForcedProcessingType());
            }
        }

        return true;
    }
}
