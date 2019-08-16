package org.mskcc.kickoff.retriever;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Request;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.util.Constants.*;

public class FileSystemFastqPathsRetriever implements FastqPathsRetriever {
    public static final String SAMPLE_SHEET_PATH = "/SampleSheet.csv";
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private final File fastqDir;

    public FileSystemFastqPathsRetriever(String fastqPath) {
        fastqDir = new File(fastqPath);
    }

    @Override
    public String retrieve(KickoffRequest request, Sample sample, String runId, String
            samplePattern) throws IOException, InterruptedException {

        String pattern = getPattern(sample, runId, fastqDir, samplePattern);
        Process process = getProcess("ls -d " + pattern);

        if (!isSucceeded(process)) {
            String igoId = sample.get(IGO_ID);
            String seqId = sample.get(SEQ_IGO_ID);

            process = getProcess("ls -d " + pattern + "_IGO_" + seqId + "*");

            if (!isSucceeded(process)) {
                process = getProcess("ls -d " + pattern + "_IGO_*");
                if (!isSucceeded(process))
                    throw new FastqDirNotFound(String.format("Fastq dir not found for sample: %s, run: %s, in dir: " +
                            "%s. Cause: %s", igoId, runId, fastqDir, getErrorMessage(process)));
            } else {
                if (!seqId.equals(igoId)) {
                    String manifestSampleID = sample.get(MANIFEST_SAMPLE_ID).replace("IGO_" + igoId, "IGO_" + seqId);
                    sample.put(MANIFEST_SAMPLE_ID, manifestSampleID);
                }
            }
        }

        String sampleFqPath = StringUtils.chomp(IOUtils.toString(process.getInputStream()));

        List<String> fastqPaths = Arrays.asList(sampleFqPath.split("\n"));
        DEV_LOGGER.info(String.format("Fastq paths: " + fastqPaths));

        List<String> matchingFastqPaths = fastqPaths.stream()
                .filter(s -> !sample.isPooledNormal() || recipeMatches(request, s))
                .collect(Collectors.toList());

        DEV_LOGGER.info(String.format("Matching Fastq paths: " + matchingFastqPaths));

        if (matchingFastqPaths.size() == 0) {
            throw new RuntimeException(String.format("No FASTQ paths found for sample: %s and run: %s", sample, runId));
        }

        String latestFastqPath = matchingFastqPaths.get(0);

        if (matchingFastqPaths.size() > 1) {
            latestFastqPath = getLatestFastqPath(sample, runId, matchingFastqPaths, latestFastqPath);
        }

        return latestFastqPath;
    }

    private boolean recipeMatches(KickoffRequest request, String s) {
        return s.matches("(.*)IGO_" + request.getBaitVersion()
                .toUpperCase() + "_[ATCG](.*)");
    }

    private String getLatestFastqPath(Sample sample, String runId, List<String> fastqPaths, String latestFastqPath) throws IOException {

        long latestLastModified = -1;
        for (String fastqPath : fastqPaths) {
            File file = new File(fastqPath);
            long lastModified = Files.getLastModifiedTime(file.toPath()).toMillis();
            if (lastModified > latestLastModified) {
                latestFastqPath = fastqPath;
                latestLastModified = lastModified;
            }

            DEV_LOGGER.info(String.format("Fastq path: %s, last modified modified: %s", fastqPath, lastModified));
        }

        DEV_LOGGER.info(String.format("There are multiple FASTQ paths for sample: %s and run: %s: %s. Choosing newest" +
                " one: %s", sample, runId, fastqPaths, latestFastqPath));

        return latestFastqPath;
    }

    private boolean isSucceeded(Process process) {
        return process.exitValue() == 0;
    }

    private Process getProcess(String cmd) throws IOException, InterruptedException {
        Process pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
        pr.waitFor();

        return pr;
    }

    private String getErrorMessage(Process pr) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
        StringBuilder stringBuilder = new StringBuilder();

        while (reader.ready())
            stringBuilder.append(reader.readLine());
        return stringBuilder.toString();
    }

    private String getPattern(Sample sample, String runId, File fastqDir, String samplePattern) {
        String pattern;
        if (sample.isPooledNormal()) {
            pattern = String.format("%s/%s*/Proj*/Sample_%s*", fastqDir.toString(), runId, samplePattern);
        } else {
            pattern = String.format("%s/%s*/Proj*%s/Sample_%s", fastqDir.toString(), runId, sample.getRequestId()
                    .replaceFirst("^0+(?!$)", ""), samplePattern);
        }
        return pattern;
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }

    @Override
    public void validateSampleSheetExist(String path, Request request, Object sample, Object runIDFull) {
        File sampleSheet = new File(path + SAMPLE_SHEET_PATH);

        if (!sampleSheet.isFile() && request.getRequestType() == RequestType.IMPACT) {
            String message = String.format("Sample %s from run %s does not have a sample sheet in the sample " +
                    "directory: %s. This will not pass the validator.", sample, runIDFull, sampleSheet.getAbsolutePath
                    ());
            PM_LOGGER.log(PmLogPriority.WARNING, message);
            DEV_LOGGER.warn(message);
        }
    }

    public class FastqDirNotFound extends RuntimeException {
        public FastqDirNotFound(String message) {
            super(message);
        }
    }
}
