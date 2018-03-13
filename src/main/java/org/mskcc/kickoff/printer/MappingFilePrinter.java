package org.mskcc.kickoff.printer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Pairedness;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.SampleInfo;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.shiny;
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

    @Value("${fastq_path}")
    private String fastq_path;

    @Autowired
    public MappingFilePrinter(Predicate<Set<Pairedness>> pairednessValidPredicate, PairednessResolver
            pairednessResolver, ObserverManager observerManager) {
        super(observerManager);
        this.pairednessValidPredicate = pairednessValidPredicate;
        this.pairednessResolver = pairednessResolver;
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
            for (KickoffRequest singleRequest : request.getRequests()) {
                for (SampleRun sampleRun : getSampleRuns(singleRequest)) {
                    Sample sample = sampleRun.getSample();
                    String sampleId = sample.getCmoSampleId();
                    final String runId = sampleRun.getRunId();

                    File dir = new File(String.format("%s/hiseq/FASTQ/", fastq_path));

                    Optional<String> optionalRunIDFull = getRunId(request, runsWithMultipleFolders, singleRequest,
                            runId, dir);
                    if (!optionalRunIDFull.isPresent()) continue;

                    String runIdFull = optionalRunIDFull.get();

                    request.addRunID(runIdFull);

                    for (String samplePattern : getSamplePatterns(sample, sampleId)) {
                        for (String path : getPaths(request, sample, dir, runIdFull, samplePattern)) {
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

            validatePairedness(pairednesses, request.getId(), request);

            return mappingFileContents.toString();
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to retrieve sample mappings for request: %s", request
                    .getId()), e);
        }
    }

    private void validatePairedness(Set<Pairedness> pairednesses, String reqId, KickoffRequest request) {
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

    private List<String> getPaths(KickoffRequest request, Sample sample, File dir, String runIDFull, String
            samplePattern) throws IOException, InterruptedException {
        String pattern = getPattern(sample, runIDFull, dir, samplePattern);
        String cmd = "ls -d " + pattern;
        Process pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
        pr.waitFor();

        //@TODO move to atnoher place, manifest file depends on new mapping so it has to be done before printing any
        // files
        int exit = pr.exitValue();
        if (exit != 0) {
            String igoID = sample.get(Constants.IGO_ID);
            String seqID = sample.get(Constants.SEQ_IGO_ID);

            cmd = "ls -d " + pattern + "_IGO_" + seqID + "*";

            pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
            pr.waitFor();
            exit = pr.exitValue();

            if (exit != 0) {
                cmd = "ls -d " + pattern + "_IGO_*";

                pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                pr.waitFor();
                exit = pr.exitValue();

                if (exit != 0) {
                    String message = String.format("Error while trying to find fastq Directory for %s it is probably " +
                            "mispelled, or has an alias.", sample);
                    Utils.setExitLater(true);
                    PM_LOGGER.log(Level.ERROR, message);
                    DEV_LOGGER.log(Level.ERROR, message);

                    observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError(message,
                            ErrorCode.FASTQ_DIR_NOT_FOUND));

                    BufferedReader bufE = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                    while (bufE.ready()) {
                        DEV_LOGGER.error(bufE.readLine());
                    }
                    request.setMappingIssue(true);
                    return Collections.emptyList();
                } else {
                    request.setNewMappingScheme(1);
                }
            } else {
                if (!seqID.equals(igoID)) {
                    String manifestSampleID = sample.get(Constants.MANIFEST_SAMPLE_ID).replace("IGO_" + igoID, "IGO_"
                            + seqID);
                    sample.put(Constants.MANIFEST_SAMPLE_ID, manifestSampleID);
                }
                request.setNewMappingScheme(1);
            }
        }
        String sampleFqPath = StringUtils.chomp(IOUtils.toString(pr.getInputStream()));

        if (sampleFqPath.isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.asList(sampleFqPath.split("\n"));
    }

    private Optional<String> getRunId(KickoffRequest request, HashSet<String> runsWithMultipleFolders, KickoffRequest
            singleRequest, String runId, File dir) {
        String RunIDFull;
        File[] files = dir.listFiles((dir1, name) -> name.startsWith(runId));

        if (files == null) {
            String message = String.format("No directories for run ID %s found.", runId);
            logWarning(message);
            return Optional.empty();
        }
        if (files.length == 0) {
            String errorMessage = String.format("Sequencing run folder not found for run id: %s in path: %s", runId,
                    dir.getPath());
            Utils.setExitLater(true);
            PM_LOGGER.log(Level.ERROR, errorMessage);
            DEV_LOGGER.log(Level.ERROR, errorMessage);
            request.setMappingIssue(true);

            observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError(errorMessage, ErrorCode
                    .SEQUENCING_FOLDER_NOT_FOUND));

            throw new NoSequencingRunFolderFoundException(errorMessage);
        } else if (files.length > 1) {
            List<File> runsWithProjectDir = Arrays.stream(files)
                    .filter(f -> {
                        File file = new File(String.format("%s/Project_%s", f.getAbsoluteFile().toString(),
                                singleRequest));
                        return file.exists() && file.isDirectory();
                    })
                    .collect(Collectors.toList());

            files = runsWithProjectDir.toArray(new File[runsWithProjectDir.size()]);

            if (files.length == 0) {
                String errorMessage = String.format("Sequencing run folder not found for run id: %s and request: %s " +
                        "in path: %s", runId, request.getId(), dir.getPath());
                Utils.setExitLater(true);
                PM_LOGGER.log(Level.ERROR, errorMessage);
                DEV_LOGGER.log(Level.ERROR, errorMessage);
                request.setMappingIssue(true);

                observerManager.notifyObserversOfError(ManifestFile.MAPPING, new GenerationError
                        (errorMessage, ErrorCode.SEQUENCING_FOLDER_NOT_FOUND));

                throw new NoSequencingRunFolderFoundException(errorMessage);
            } else if (files.length > 1) {
                Arrays.sort(files, Comparator.comparingLong(File::lastModified));
                String foundFiles = StringUtils.join(files, ", ");
                RunIDFull = files[files.length - 1].getAbsoluteFile().getName().toString();

                if (!runsWithMultipleFolders.contains(runId)) {
                    String message = String.format("More than one sequencing run folder found for Run ID %s: %s I " +
                            "will be picking the newest folder: %s.", runId, foundFiles, RunIDFull);
                    logWarning(message);
                    runsWithMultipleFolders.add(runId);
                }
            } else {
                RunIDFull = files[0].getAbsoluteFile().getName();
            }
        } else {
            RunIDFull = files[0].getAbsoluteFile().getName();
        }
        return Optional.of(RunIDFull);
    }

    private void validateSampleSheetExists(KickoffRequest request, Sample sample, String runIDFull, String path) {
        File samp_sheet = new File(path + "/SampleSheet.csv");
        if (!samp_sheet.isFile() && request.getRequestType() == RequestType.IMPACT) {
            String message = String.format("Sample %s from run %s does not have a sample sheet in the sample " +
                    "directory: %s. This will not pass the validator.", sample, runIDFull, samp_sheet.getAbsolutePath
                    ());
            if (shiny) {
                PM_LOGGER.error(message);
            } else {
                PM_LOGGER.log(PmLogPriority.WARNING, message);
            }
            DEV_LOGGER.warn(message);
        }
    }

    private String getPattern(Sample sample, String runIDFull, File dir, String samplePattern) {
        String pattern;
        if (!isPooledNormalSample(sample)) {
            pattern = String.format("%s/%s*/Proj*%s/Sample_%s", dir.toString(), runIDFull, sample.getRequestId()
                    .replaceFirst("^0+(?!$)", ""), samplePattern);
        } else {
            pattern = String.format("%s/%s*/Proj*/Sample_%s*", dir.toString(), runIDFull, samplePattern);
        }
        return pattern;
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

    private boolean isPooledNormalSample(Sample sample) {
        //@TODO check if can use sample.isPooledNormal() instead
        return Objects.equals(sample.getCmoSampleId(), Constants.FFPEPOOLEDNORMAL)
                || Objects.equals(sample.getCmoSampleId(), Constants.FROZENPOOLEDNORMAL)
                || Objects.equals(sample.getCmoSampleId(), Constants.MOUSEPOOLEDNORMAL);
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

    private class NoSequencingRunFolderFoundException extends RuntimeException {
        public NoSequencingRunFolderFoundException(String message) {
            super(message);
        }
    }

    private class NoSampleMappingExistException extends RuntimeException {
        public NoSampleMappingExistException(String message) {
            super(message);
        }
    }

}
