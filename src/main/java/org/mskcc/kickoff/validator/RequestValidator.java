package org.mskcc.kickoff.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Pool;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.logger.PriorityAwareLogMessage;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.forced;
import static org.mskcc.kickoff.config.Arguments.outdir;

public class RequestValidator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private List<PriorityAwareLogMessage> poolQCWarnings = new ArrayList<>();

    public void validate(Request request) {
        validateOutputDir(request);
        validateAutoGenerability(request);
        validateSampSpecificQc(request);
        validatePoolSeqQc(request);
        validateSampleQcAgainstPoolQC(request);
        validatePostSeqQC(request);
//        validateNimbleGen(request);
        validateHasSamples(request);
        validateBarcodeInfo(request);
        validateReadCounts(request);
    }

    private void validateOutputDir(Request request) {
        //@TODO after finishing comparing with current prod version refactor it to check outdir only once, as for now it stays fo tests to pass
        if(!StringUtils.isEmpty(outdir)) {
            File outputDir = new File(outdir);

            if (!StringUtils.isEmpty(outdir)) {
                if (outputDir.exists() && outputDir.isDirectory()) {
                    String message = String.format("Overwriting default dir to %s", request.getOutputPath());
                    PM_LOGGER.info(message);
                    DEV_LOGGER.info(message);
                } else {
                    String message = String.format("The outdir directory you gave me is empty or does not exist: %s", outdir);
                    Utils.setExitLater(true);
                    PM_LOGGER.error(message);
                    DEV_LOGGER.error(message);
                }
            }
        }
    }

    private void validateNimbleGen(Request request) {
        for (Sample sample : request.getAllValidSamples().values()) {
            //@TODO check how to check better for lack of nibmlegen, capture_input is temporary
            if (StringUtils.isEmpty(sample.get(Constants.CAPTURE_INPUT))) {
                Utils.setExitLater(true);
            }
        }
    }

    private void validateHasSamples(Request request) {
        if (request.getAllValidSamples().size() == 0)
            throw new RuntimeException(String.format("There are no samples in request: %s", request.getId()));
    }

    public void validateSampleUniqueness(Request request) {
        Set<Sample> duplicateSamples = getDuplicateSamples(request);
        if (duplicateSamples.size() > 0) {
            throw new RuntimeException(String.format("This request has duplicate samples names: %s", StringUtils.join(duplicateSamples, ",")));
        }
    }

    private Set<Sample> getDuplicateSamples(Request request) {
        Collection<Sample> samples = request.getSamples().values();
        return samples.stream().filter(s -> Collections.frequency(samples, s) > 1).collect(Collectors.toSet());
    }

    private void validateBarcodeInfo(Request request) {
        for (Sample sample : request.getValidNonPooledNormalSamples().values()) {
            if ((request.getRequestType().equals(Constants.IMPACT) || request.getRequestType().equals(Constants.EXOME))
                    && sample.getRuns().values().stream().allMatch(r -> r.getSampleLevelQcStatus() == null)) {
                Utils.setExitLater(true);
                String message = String.format("Unable to get barcode for %s AKA: %s", sample.getIgoId(), sample.getCmoSampleId());
                DEV_LOGGER.error(message); //" there must be a sample specific QC data record that I can search up from");
                PM_LOGGER.error(message); //" there must be a sample specific QC data record that I can search up from");

                //@TODO I think barcode ID should be checked but done as above to have same results as prod version
/*
                String barcodeId = sample.getProperties().get(Constants.BARCODE_ID);
                if (StringUtils.isEmpty(barcodeId) || Objects.equals(barcodeId, Constants.EMPTY))
                    logError(String.format("Unable to get barcode for %s AKA: %s", sample.getProperties().get(Constants.IGO_ID), sample.getProperties().get(Constants.CMO_SAMPLE_ID))); //" there must be a sample specific QC data record that I can search up from");
*/
            }
        }
    }

    private void validateSampSpecificQc(Request request) {
        //@TODO predicate chain?
        Collection<Sample> nonPooledNormalUniqueSamples = request.getNonPooledNormalUniqueSamples(Sample::getCmoSampleId);
        for (Sample sample : nonPooledNormalUniqueSamples) {
            Collection<Run> runs = sample.getRuns().values();
            for (Run run : runs) {
                if (run.getSampleLevelQcStatus() != null && run.getSampleLevelQcStatus() != QcStatus.PASSED) {
                    if (run.getSampleLevelQcStatus() == QcStatus.FAILED || run.getSampleLevelQcStatus() == QcStatus.FAILED_REPROCESS) {
                        String message = String.format("Not including Sample %s from Run ID %s because it did NOT pass Sequencing Analysis QC: %s", sample, run.getId(), run.getSampleLevelQcStatus());
                        PM_LOGGER.log(PmLogPriority.SAMPLE_INFO, message);
                        DEV_LOGGER.log(Level.INFO, message);
                    } else if (run.getSampleLevelQcStatus() == QcStatus.UNDER_REVIEW) {
                        String message = String.format("Sample %s from RunID %s is still under review. I cannot guarantee this is DONE!", sample, run.getId());
                        Utils.setExitLater(true);
                        PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                        request.setMappingIssue(true);
                    } else {
                        String message = String.format("Sample %s from RunID %s needed additional reads. This status should change when the extra reads are sequenced. Please check. ", sample, run);
                        Utils.setExitLater(true);
                        PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                        request.setMappingIssue(true);
                    }
                    continue;
                }
            }
            if (sample.getAlias() != null && !sample.getAlias().isEmpty()) {
                String message = "SAMPLE " + sample.getIgoId() + " HAS AN ALIAS: " + sample.getAlias() + "!";
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }
    }

    private void validatePoolSeqQc(Request request) {
        for (Pool pool : request.getPools().values()) {
            for (Run run : pool.getRuns().values()) {
                if (run.getPoolQcStatus() != QcStatus.PASSED) {
                    if (run.getPoolQcStatus() == QcStatus.FAILED || run.getPoolQcStatus() == QcStatus.FAILED_REPROCESS) {
                        String message = "Skipping Run ID " + run.getId() + " because it did NOT pass Sequencing Analysis QC: " + run.getPoolQcStatus();
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_INFO, message));
                        continue;
                    } else if (run.getPoolQcStatus() == QcStatus.UNDER_REVIEW) {
                        String message = "RunID " + run.getId() + " is still under review for pool " + pool.getIgoId() + " I cannot guarantee this is DONE!";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        request.setMappingIssue(true);
                    } else {
                        String message = "RunID " + run.getId() + " needed additional reads. I cannot tell yet if they were finished. Please check.";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.setExitLater(true);
                        request.setMappingIssue(true);
                    }
                }

                if (Objects.equals(run.getId(), Constants.NULL)) {
                    String message = "Unable to find run path or related sample ID for this sequencing run";
                    poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                    Utils.setExitLater(true);
                    request.setMappingIssue(true);
                }
            }
        }
    }

    private void validateAutoGenerability(Request request) {
        boolean autoGenAble = request.isBicAutorunnable();

        if (!autoGenAble) {
            String reason = "";
            String readMe = request.getReadMe();
            String[] bicReadmeLines = readMe.split("\\n\\r|\\n");
            for (String bicLine : bicReadmeLines) {
                if (bicLine.startsWith("NOT_AUTORUNNABLE")) {
                    reason = "REASON: " + bicLine;
                    break;
                }
            }
            String requestID = request.getId();
            String message = String.format("According to the LIMS, project %s cannot be run through this script. %s Sorry :(", requestID, reason);
            Utils.setExitLater(true);
            PM_LOGGER.log(Level.ERROR, message);
            DEV_LOGGER.log(Level.ERROR, message);

            if (!Arguments.krista)
                throw new RuntimeException(String.format("Request id: %s cannot be run through this script", requestID));
        }
    }

    private void validatePostSeqQC(Request request) {
        // This will look through all post seq QC records
        // For each, if sample AND run is in map
        // then check the value of PostSeqQCStatus
        // if passing, continue
        // if failed, output WARNING that this sample failed post seq qc and WHY
        //            remove the run id

        // I'm going to add Sample ID and run ID here when I'm done yo! That way I can check and see if any
        // Don't have PostSeqAnalysisQC

        for (Sample sample : request.getSamples(s -> !s.isPooledNormal()).values()) {
            for (Run run : sample.getRuns().values()) {
                if (run.isSampleQcPassed()) {
                    QcStatus status = run.getPostQcStatus();
                    if (status != null && status != QcStatus.PASSED) {
                        String note = run.getNote();
                        note = note.replaceAll("\n", " ");
                        String message = String.format("Sample %s in run %s did not pass POST Sequencing QC(%s). The note attached says: %s. This will not be included in manifest.",
                                sample.getCmoSampleId(), run.getId(), status, note);
                        PM_LOGGER.log(PmLogPriority.WARNING, message);
                        DEV_LOGGER.warn(message);
                    } else {
                        sample.addRun(run);
                    }
                }
            }
        }

        for (Sample sample : request.getSamples(s -> !s.isPooledNormal()).values()) {
            Set<Run> runsWithoutPostQc = sample.getRuns().values().stream().filter(r -> r.getPostQcStatus() == null).collect(Collectors.toSet());
            if (runsWithoutPostQc.size() > 0) {
                String message = String.format("Sample %s has runs that do not have POST Sequencing QC. We won't be able to tell if they are failed or not: %s. They will still be added to the sample list.",
                        sample.getCmoSampleId(), Arrays.toString(runsWithoutPostQc.toArray()));
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }
    }

    private void validateReadCounts(Request request) {
        // For each sample in readsForSample if the number of reads is less than 1M, print out a warning
        for (Sample sample : getSamplesWithSampleQc(request)) {
            int numberOfReads = sample.getNumberOfReads();
            if (numberOfReads < Constants.MINIMUM_NUMBER_OF_READS) {
                // Print AND add to readme file
                String extraReadmeInfo = String.format("\n[WARNING] sample %s has less than 1M total reads: %d\n", sample, numberOfReads);
                request.setExtraReadMeInfo(request.getExtraReadMeInfo() + extraReadmeInfo);
                String message = String.format("sample %s has less than 1M total reads: %d", sample, numberOfReads);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }
    }

    private Collection<Sample> getSamplesWithSampleQc(Request request) {
        return request.getSamples(s -> s.getRuns().values().stream().anyMatch(r -> r.getSampleLevelQcStatus() == QcStatus.PASSED)).values();
    }

    private void validateSampleQcAgainstPoolQC(Request request) {
        // If sample and pool have the same number of samples and runIDs, return sample (more accurate information)
        // If pool has more RunIDs than sample, then use pool's data, with whatever sample specific overlap available.
        //     write warning about it
        // If sample has more run IDs than pool, send an error out because that should never happen.
        Set<Run> runsWithSampQc = request
                .getSamples()
                .values()
                .stream()
                .map(s -> s.getRuns().values())
                .flatMap(r -> r.stream())
                .filter(r -> r.getSampleLevelQcStatus() != null)
                .collect(Collectors.toSet());

        Set<Run> runsWithPoolQc = request
                .getPools()
                .values()
                .stream()
                .map(s -> s.getRuns().values())
                .flatMap(r -> r.stream())
                .filter(r -> r.getPoolQcStatus() != null)
                .collect(Collectors.toSet());


        if (runsWithSampQc.size() == 0) {
            if (runsWithPoolQc.size() != 0 & !request.isManualDemux()) {
                Utils.setExitLater(true);
                String message = "For this project the sample specific QC is not present. My script is looking at the pool specific QC instead, which assumes that if the pool passed, every sample in the pool also passed. This is not necessarily true and you might want to check the delivery email to make sure it includes every sample name that is on the manifest. This doesn’t mean you can’t put the project through, it just means that the script doesn’t know if the samples failed sequencing QC.";
                PM_LOGGER.log(Level.ERROR, message);
                DEV_LOGGER.log(Level.ERROR, message);
                request.setMappingIssue(true);
            }
            printPoolQCWarnings();
        } else if (poolQCWarnings.stream().anyMatch(w -> w.getPriority() == PmLogPriority.POOL_ERROR)) {
            printPoolQCWarnings();
        }

        Set<Run> runsWithNonUnderReviewPoolQc = runsWithPoolQc.stream().filter(r -> r.getPoolQcStatus() != QcStatus.UNDER_REVIEW).collect(Collectors.toSet());
        runsWithNonUnderReviewPoolQc.removeAll(runsWithSampQc);

        if (!runsWithNonUnderReviewPoolQc.isEmpty()) {
            String message = "Sample specific QC is missing run(s) that pool QC contains. Will add POOL QC data for missing run(s): " + StringUtils.join(runsWithNonUnderReviewPoolQc, ", ");
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

    public void validateSamplesExist(Request request) {
        int numberOfValidNonNormalSamples = request.getValidNonPooledNormalSamples().size();
        if (numberOfValidNonNormalSamples == 0) {
            Utils.setExitLater(true);
            String message = "None of the samples in the project were found in the passing samples and runs. Please check the LIMs to see if the names are incorrect.";
            PM_LOGGER.log(Level.ERROR, message);
            DEV_LOGGER.log(Level.ERROR, message);
        }
        if (request.getSamples().size() == 0)
            throw new RuntimeException(String.format("No samples found for request: %s", request.getId()));
    }
}