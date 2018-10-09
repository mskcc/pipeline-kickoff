package org.mskcc.kickoff.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Pool;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.logger.PriorityAwareLogMessage;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.*;

@Component
public class RequestValidator {
    static final int MAX_NUMBER_OF_SAMPLES = 200;
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final PairingsValidator pairingsValidator;
    private final List<PriorityAwareLogMessage> poolQCWarnings = new ArrayList<>();
    private final ErrorRepository errorRepository;

    @Autowired
    public RequestValidator(PairingsValidator pairingsValidator, ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
        this.pairingsValidator = pairingsValidator;
    }

    public void validate(KickoffRequest kickoffRequest) {
        validateAutoGenerability(kickoffRequest);
        validateSampSpecificQc(kickoffRequest);
        validatePoolSeqQc(kickoffRequest);
        validateSampleQcAgainstPoolQC(kickoffRequest);
        validatePostSeqQC(kickoffRequest);
        validateSequencingRuns(kickoffRequest);
        validateBarcodeInfo(kickoffRequest);
        validateReadCounts(kickoffRequest);
        validateShiny(kickoffRequest);
        validateSampleUniqueness(kickoffRequest);
        validateOutputDir(kickoffRequest);
        validateHasValidSamples(kickoffRequest);
        validateSamplesExist(kickoffRequest);
        validatePairings(kickoffRequest);
    }

    private void validateHasValidSamples(KickoffRequest kickoffRequest) {
        try {
            kickoffRequest.validateHasSamples();
        } catch (KickoffRequest.NoValidSamplesException e) {
            errorRepository.add(new GenerationError(e.getMessage(), ErrorCode.NO_VALID_SAMPLES));
            throw e;
        }
    }

    private void validatePairings(KickoffRequest kickoffRequest) {
        if (!kickoffRequest.isPairingError())
            pairingsValidator.isValid(kickoffRequest.getPairingInfos());
    }

    private void validateShiny(KickoffRequest request) {
        if (shiny && request.getRequestType() == RequestType.RNASEQ)
            throw new RuntimeException("This is an RNASeq project, and you cannot grab this information yet via Shiny");
    }

    private void validateOutputDir(KickoffRequest kickoffRequest) {
        //@TODO after finishing comparing with current prod version refactor it to check outdir only once, as for now
        // it stays fo tests to pass
        if (!StringUtils.isEmpty(outdir)) {
            File outputDir = new File(outdir);

            if (!StringUtils.isEmpty(outdir)) {
                if (outputDir.exists() && outputDir.isDirectory()) {
                    String message = String.format("Overwriting default dir to %s", kickoffRequest.getOutputPath());
                    PM_LOGGER.info(message);
                    DEV_LOGGER.info(message);
                } else {
                    String message = String.format("The outdir directory you gave me is empty or does not exist: %s",
                            outdir);
                    Utils.setExitLater(true);
                    PM_LOGGER.error(message);
                    DEV_LOGGER.error(message);
                }
            }
        }
    }

    private void validateNimbleGen(KickoffRequest kickoffRequest) {
        for (Sample sample : kickoffRequest.getAllValidSamples().values()) {
            //@TODO check how to check better for lack of nibmlegen, capture_input is temporary
            if (StringUtils.isEmpty(sample.get(Constants.CAPTURE_INPUT))) {
                Utils.setExitLater(true);
            }
        }
    }

    private void validateSequencingRuns(KickoffRequest kickoffRequest) {
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
    }

    public void validateSampleUniqueness(KickoffRequest kickoffRequest) {
        Set<String> duplicateSamples = getDuplicateSamples(kickoffRequest);
        if (hasDuplicateSamples(duplicateSamples)) {
            duplicateSamples.forEach(s -> {
                String message = String.format("This request has two samples that have the same name: %s", s);
                PM_LOGGER.error(message);
                DEV_LOGGER.error(message);
                errorRepository.add(new GenerationError(String.format("This request has two samples that have the " +
                        "same name: %s", s), ErrorCode.DUPLICATE_SAMPLE));
            });
            Utils.setExitLater(true);
        }
    }

    private boolean hasDuplicateSamples(Set<String> duplicateSamples) {
        return duplicateSamples.size() > 0;
    }

    private Set<String> getDuplicateSamples(KickoffRequest kickoffRequest) {
        Set<String> uniqueCmoSampleIds = new HashSet<>();

        return kickoffRequest.getValidNonPooledNormalSamples().values().stream()
                .map(Sample::getCmoSampleId)
                .filter(cmoSampleId -> !uniqueCmoSampleIds.add(cmoSampleId))
                .collect(Collectors.toSet());
    }

    private void validateBarcodeInfo(KickoffRequest kickoffRequest) {
        for (Sample sample : kickoffRequest.getValidNonPooledNormalSamples().values()) {
            if ((kickoffRequest.isExome() || kickoffRequest.isImpact()) && !sample.hasBarcode()) {
                Utils.setExitLater(true);
                String message = String.format("Unable to get barcode for %s AKA: %s", sample.getIgoId(), sample
                        .getCmoSampleId());
                DEV_LOGGER.error(message);
                PM_LOGGER.error(message);

                //@TODO I think barcode ID should be checked but done as above to have same results as prod version
/*
                String barcodeId = sample.getProperties().get(Constants.BARCODE_ID);
                if (StringUtils.isEmpty(barcodeId) || Objects.equals(barcodeId, Constants.EMPTY))
                    logError(String.format("Unable to get barcode for %s AKA: %s", sample.getProperties().get
                    (Constants.IGO_ID), sample.getProperties().get(Constants.CMO_SAMPLE_ID))); //" there must be a
                    sample specific QC data record that I can search up from");
*/
            }
        }
    }

    private void validateSampSpecificQc(KickoffRequest kickoffRequest) {
        //@TODO predicate chain?
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

    private void validatePoolSeqQc(KickoffRequest kickoffRequest) {
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
                    } else if (run.getPoolQcStatus() == QcStatus.REQUIRED_ADDITIONAL_READS) {
                        String message = "RunID " + run.getId() + " needed additional reads. I cannot tell yet if " +
                                "they were finished. Please check.";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.RUN_NEEDS_ADDITIONAL_READS));
                    } else if (run.getPoolQcStatus() == null) {
                        String message = "RunID " + run.getId() + " has no Sequencing QC Result field set in " +
                                "Sequencing Analysis QC Results record. Fill it in in LIMS.";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        kickoffRequest.setMappingIssue(true);
                        errorRepository.add(new GenerationError(message, ErrorCode.NO_QC_FOR_RUN));
                    }
                }

                if (Objects.equals(run.getId(), Constants.NULL)) {
                    String message = "Unable to find run path or related sample ID for this sequencing run";
                    poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                    Utils.setExitLater(true);
                    kickoffRequest.setMappingIssue(true);
                }
            }
        }
    }

    private void validateAutoGenerability(KickoffRequest kickoffRequest) {
        boolean autoGenAble = kickoffRequest.isBicAutorunnable();

        if (!autoGenAble) {
            String reason = "";
            String readMe = kickoffRequest.getReadMe();
            String[] bicReadmeLines = readMe.split("\\n\\r|\\n");
            for (String bicLine : bicReadmeLines) {
                if (bicLine.startsWith("NOT_AUTORUNNABLE")) {
                    reason = "REASON: " + bicLine;
                    break;
                }
            }
            String requestID = kickoffRequest.getId();
            String message = String.format("According to the LIMS, project %s cannot be run through this script. %s " +
                    "Sorry :(", requestID, reason);
            Utils.setExitLater(true);
            PM_LOGGER.log(Level.ERROR, message);

            String msg = String.format("Bic Autorunnable option is not chosen in LIMS. Request id: %s cannot " +
                    "be run automatically", requestID);

            DEV_LOGGER.log(Level.ERROR, msg);
            errorRepository.add(new GenerationError(msg, ErrorCode.NOT_AUTORUNNABLE));

            throw new RuntimeException(msg);
        }
    }

    private void validatePostSeqQC(KickoffRequest kickoffRequest) {
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
    }

    private void validateReadCounts(KickoffRequest kickoffRequest) {
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

        kickoffRequest.setReadmeInfo(String.format("%s %s", kickoffRequest.getReadMe(), kickoffRequest
                .getExtraReadMeInfo()));
    }

    private Collection<Sample> getSamplesWithSampleQc(KickoffRequest kickoffRequest) {
        return kickoffRequest.getSamples(s -> s.getRuns().values().stream().anyMatch(r -> r.getSampleLevelQcStatus()
                == QcStatus.PASSED)).values();
    }

    private void validateSampleQcAgainstPoolQC(KickoffRequest kickoffRequest) {
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

    public void validateSamplesExist(KickoffRequest kickoffRequest) {
        int numberOfValidNonNormalSamples = kickoffRequest.getValidNonPooledNormalSamples().size();
        if (numberOfValidNonNormalSamples == 0) {
            Utils.setExitLater(true);
            String message = "None of the samples in the request were found in the passing samples and runs. Please " +
                    "check the LIMs to see if the names are incorrect.";
            PM_LOGGER.log(Level.ERROR, message);
            DEV_LOGGER.log(Level.ERROR, message);
            errorRepository.add(new GenerationError(message, ErrorCode.NO_PASSING_SAMPLES));
        }

        if (kickoffRequest.getSamples().size() == 0) {
            String message = String.format("No samples found for request: %s", kickoffRequest.getId());
            errorRepository.add(new GenerationError(message, ErrorCode.NO_SAMPLES));
            throw new RuntimeException(message);
        }
    }
}
