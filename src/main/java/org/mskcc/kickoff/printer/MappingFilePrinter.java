package org.mskcc.kickoff.printer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Pairedness;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.SampleInfo;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.retriever.FastqPathsRetriever;
import org.mskcc.kickoff.retriever.FileSystemFastqPathsRetriever;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

@Component
public class MappingFilePrinter extends FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);

    private static final String NORMAL_MAPPING_FILE_NAME = "sample_mapping.txt";
    private static final String ERROR_MAPPING_FILE_NAME = "sample_mapping.error";

    private final Predicate<Set<Pairedness>> pairednessValidPredicate;
    private final PairednessResolver pairednessResolver;
    private final FastqPathsRetriever fastqPathsRetriever;

    @Autowired
    public MappingFilePrinter(Predicate<Set<Pairedness>> pairednessValidPredicate, PairednessResolver
            pairednessResolver, ObserverManager observerManager,
                              FastqPathsRetriever fastqPathsRetriever) {
        super(observerManager);
        this.pairednessValidPredicate = pairednessValidPredicate;
        this.pairednessResolver = pairednessResolver;
        this.fastqPathsRetriever = fastqPathsRetriever;
    }

    @Override
    public void print(KickoffRequest request) {
        DEV_LOGGER.info(String.format("Starting to create file: %s", getFilePath(request)));
        try {
            String mappingFileContents = getMappings(request);
            writeMappingFile(request, mappingFileContents);
        } catch (Exception e) {
            DEV_LOGGER.error(String.format("Unable to create mapping file: %s", getFilePath(request)), e);
        }
    }

    private String getMappings(KickoffRequest request) {
        try {
            Map<String, String> sampleRenamesAndSwaps = SampleInfo.getSampleRenames();
            HashSet<String> runsWithMultipleFolders = new HashSet<>();

            Set<Pairedness> pairednesses = new HashSet<>();
            StringBuilder mappingFileContents = new StringBuilder();

            printIgoSamples(request, sampleRenamesAndSwaps, runsWithMultipleFolders, pairednesses, mappingFileContents);
            printExternalSamples(request, mappingFileContents);

            validatePairedness(pairednesses, request.getId());

            return mappingFileContents.toString();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to retrieve sample mappings for request: %s", request
                    .getId()), e);
        }
    }

    private void printExternalSamples(KickoffRequest request, StringBuilder mappingFileContents) {
        for (KickoffExternalSample externalSample : request.getExternalSamples().values()) {
            mappingFileContents.append(String.format("_1\t%s\t%s\t%s\t%s\n", sampleNormalization(externalSample
                            .getCmoId()),
                    externalSample.getRunId(),
                    externalSample.getFilePath(), Constants.PAIRED_END));
        }
    }

    private void printIgoSamples(KickoffRequest request, Map<String, String> sampleRenamesAndSwaps, HashSet<String>
            runsWithMultipleFolders, Set<Pairedness> pairednesses, StringBuilder mappingFileContents) throws
            IOException, InterruptedException {

        Set<String> processedSamplesRuns = new HashSet<>();

        try {
            for (KickoffRequest singleRequest : request.getRequests()) {
                for (SampleRun sampleRun : getSampleRuns(singleRequest)) {
                    Sample sample = sampleRun.getSample();
                    String sampleId = sample.getCmoSampleId();
                    final String runId = sampleRun.getRunId();

                    Optional<String> optionalRunIDFull = fastqPathsRetriever.getRunId(request, runsWithMultipleFolders,
                            singleRequest, runId);
                    if (!optionalRunIDFull.isPresent()) continue;

                    String runIdFull = optionalRunIDFull.get();

                    String sampleIDrunIdFull = sampleId + runIdFull;
                    if (processedSamplesRuns.contains(sampleIDrunIdFull)) {
                        String message = String.format("Skipping Sequencing run [%s] for igo sample [%s]: already " +
                                "included.", runIdFull, sampleId);
                        logWarning(message);
                        continue;
                    }

                    processedSamplesRuns.add(sampleIDrunIdFull);
                    request.addRunID(runIdFull);

                    for (String samplePattern : getSamplePatterns(sample, sampleId)) {
                        for (String path : getPaths(request, sample, runIdFull, samplePattern)) {
                            if (isPooledNormal(sampleId) && !fastqExist(path, request.getBaitVersion()))
                                continue;

                            Pairedness pairedness = getPairedness(path);
                            DEV_LOGGER.trace(String.format("Pairedness for sample: %s - %s", sampleId, pairedness));
                            pairednesses.add(pairedness);

                            validateSampleSheetExists(request, sample, runIdFull, path);
                            String sampleName = sampleNormalization(sampleRenamesAndSwaps.getOrDefault(sampleId,
                                    sampleId));
                            mappingFileContents.append(String.format("_1\t%s\t%s\t%s\t%s\n", sampleName, runIdFull,
                                    path, pairedness));
                        }
                    }
                }
            }
        } catch (NoSequencingRunFolderFoundException e) {
            Utils.setExitLater(true);
            PM_LOGGER.log(Level.ERROR, e.getMessage());
            DEV_LOGGER.log(Level.ERROR, e.getMessage());
            request.setMappingIssue(true);

            observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError(e.getMessage(), ErrorCode
                    .SEQUENCING_FOLDER_NOT_FOUND));
            throw e;
        }
    }

    private void validatePairedness(Set<Pairedness> pairednesses, String reqId) {
        if (!pairednessValidPredicate.test(pairednesses)) {
            String message = String.format("Ambiguous pairedness for request: %s [%s]", reqId, StringUtils.join
                    (pairednesses), ",");
            PM_LOGGER.error(message);
            DEV_LOGGER.error(message);
            Utils.setExitLater(true);

            observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError(message, ErrorCode
                    .AMBIGUOUS_PAIREDNESS));
        }
    }

    private Pairedness getPairedness(String path) {
        try {
            return pairednessResolver.resolve(path);
        } catch (IOException e) {
            GenerationError generationError = new GenerationError(String.format("Unable to retrieve pairedness from " +
                    "path: %s", path), ErrorCode.PAIREDNESS_RETRIEVAL_ERROR);
            observerManager.notifyObserversOfError(ManifestFile.MAPPING, generationError);

            throw new RuntimeException(generationError.getMessage());
        }
    }

    private List<String> getPaths(KickoffRequest request, Sample sample, String runIDFull, String
            samplePattern) throws IOException, InterruptedException {
        List<String> fastqPaths = Collections.emptyList();

        //@TODO move to another place, manifest file depends on new mapping so it has to be done before printing any
        try {
            fastqPaths = fastqPathsRetriever.retrieve(request, sample, runIDFull, samplePattern);
            request.setNewMappingScheme(1);
        } catch (FileSystemFastqPathsRetriever.FastqDirNotFound e) {
            String message = String.format("Error while trying to find fastq for %s it is probably " +
                    "mispelled, or has an alias.", sample);
            Utils.setExitLater(true);
            PM_LOGGER.log(Level.ERROR, message);
            DEV_LOGGER.log(Level.ERROR, e.getMessage());

            observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError(message,
                    ErrorCode.FASTQ_NOT_FOUND));

            request.setMappingIssue(true);
        }

        return fastqPaths;
    }

    private void validateSampleSheetExists(KickoffRequest request, Sample sample, String runIDFull, String path) {
        fastqPathsRetriever.validateSampleSheetExist(path, request, sample, runIDFull);
    }

    private Set<String> getSamplePatterns(Sample sample, String sampleId) {
        Set<String> samplePatterns = new HashSet<>();
        if (!StringUtils.isEmpty(sample.getAlias())) {
            ArrayList<String> aliasSampleNames = new ArrayList<>(Arrays.asList(sample.getAlias().split(";")));
            for (String aliasName : aliasSampleNames) {
                String message = String.format("Sample %s has alias %s", sample, aliasName);
                logWarning(message);
                samplePatterns.add(aliasName.replaceAll("[_-]", "[-_]"));
            }
        } else {
            samplePatterns.add(sampleId.replaceAll("[_-]", "[-_]"));
        }
        return samplePatterns;
    }

    private Set<SampleRun> getSampleRuns(KickoffRequest singleRequest) {
        Set<SampleRun> sampleRuns = new LinkedHashSet<>();
        for (Sample sample : singleRequest.getAllValidSamples().values()) {
            for (String runId : sample.getValidRunIds()) {
                sampleRuns.add(new SampleRun(sample, runId));
            }
        }
        return sampleRuns;
    }

    private void writeMappingFile(KickoffRequest request, String mappingFileContents) {
        validateMappingsExist(request, mappingFileContents);

        try {
            mappingFileContents = filterToAscii(mappingFileContents);
            File mappingFile = new File(getFilePath(request));
            PrintWriter pW = new PrintWriter(new FileWriter(mappingFile, false), false);
            pW.write(mappingFileContents);
            pW.close();
            observerManager.notifyObserversOfFileCreated(ManifestFile.MAPPING);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to write sample mapping file for request: %s", request
                    .getId()));
        }
    }

    private void validateMappingsExist(KickoffRequest request, String mappingFileContents) {
        if (StringUtils.isEmpty(mappingFileContents)) {
            String message = String.format("No sample mappings for request: %s", request.getId());
            observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError(message,
                    ErrorCode.NO_SAMPLE_MAPPINGS));
            throw new NoSampleMappingExistException(message);
        }
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        String mappingFileName = shouldOutputErrorFile(request) ? ERROR_MAPPING_FILE_NAME : NORMAL_MAPPING_FILE_NAME;
        return String.format("%s/%s_%s", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId())
                , mappingFileName);
    }

    private boolean isPooledNormal(String sampleId) {
        return sampleId.contains("POOLEDNORMAL") && (sampleId.contains("FFPE") || sampleId.contains("FROZEN"));
    }

    private boolean fastqExist(String path, String baitVersion) {
        Pattern pattern = Pattern.compile(String.format("(.*)IGO_%s_[ATCG](.*)", baitVersion), Pattern
                .CASE_INSENSITIVE);
        return pattern.matcher(path).matches();
    }

    private boolean shouldOutputErrorFile(KickoffRequest request) {
        return Utils.isExitLater()
                && request.isMappingIssue()
                && !request.isInnovation()
                && request.getRequestType() != RequestType.RNASEQ
                && request.getRequestType() != RequestType.OTHER;
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return ((request.getRequestType() == RequestType.RNASEQ || request.getRequestType() == RequestType.OTHER)
                && !request.isForced()
                && !request.isManualDemux()
        )
                || !request.isForced();
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }

    public static class NoSequencingRunFolderFoundException extends RuntimeException {
        public NoSequencingRunFolderFoundException(String message) {
            super(message);
        }
    }

    class SampleRun {
        private final Sample sample;
        private final String runId;

        SampleRun(Sample sample, String runId) {
            this.sample = sample;
            this.runId = runId;
        }

        public Sample getSample() {
            return sample;
        }

        public String getRunId() {
            return runId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SampleRun sampleRun = (SampleRun) o;

            return sample.getCmoSampleId().equals(sampleRun.sample.getCmoSampleId()) && runId.equals(sampleRun.runId);
        }

        @Override
        public int hashCode() {
            int result = sample.getCmoSampleId().hashCode();
            result = 31 * result + runId.hashCode();
            return result;
        }
    }

    private class NoSampleMappingExistException extends RuntimeException {
        public NoSampleMappingExistException(String message) {
            super(message);
        }
    }

}
