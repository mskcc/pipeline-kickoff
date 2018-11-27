package org.mskcc.kickoff.retriever;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Request;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.printer.FastqPathsRetriever;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.util.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
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
    public List<String> retrieve(KickoffRequest request, Sample sample, String runIDFull, String
            samplePattern) throws IOException, InterruptedException {

        String pattern = getPattern(sample, runIDFull, fastqDir, samplePattern);
        Process process = getProcess("ls -d " + pattern);

        if (!isSucceeded(process)) {
            String igoId = sample.get(IGO_ID);
            String seqId = sample.get(SEQ_IGO_ID);

            process = getProcess("ls -d " + pattern + "_IGO_" + seqId + "*");

            if (!isSucceeded(process)) {
                process = getProcess("ls -d " + pattern + "_IGO_*");
                if (!isSucceeded(process))
                    throw new FastqDirNotFound(String.format("Fastq dir not found for sample: %s, run: %s, in dir: " +
                            "%s. Cause: %s", igoId, runIDFull, fastqDir, getErrorMessage(process)));
            } else {
                if (!seqId.equals(igoId)) {
                    String manifestSampleID = sample.get(MANIFEST_SAMPLE_ID).replace("IGO_" + igoId, "IGO_" + seqId);
                    sample.put(MANIFEST_SAMPLE_ID, manifestSampleID);
                }
            }
        }

        String sampleFqPath = StringUtils.chomp(IOUtils.toString(process.getInputStream()));

        if (sampleFqPath.isEmpty())
            return Collections.emptyList();

        return Arrays.asList(sampleFqPath.split("\n"));
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

    private String getPattern(Sample sample, String runIDFull, File fastqDir, String samplePattern) {
        String pattern;
        if (sample.isPooledNormal()) {
            pattern = String.format("%s/%s*/Proj*/Sample_%s*", fastqDir.toString(), runIDFull, samplePattern);
        } else {
            pattern = String.format("%s/%s*/Proj*%s/Sample_%s", fastqDir.toString(), runIDFull, sample.getRequestId()
                    .replaceFirst("^0+(?!$)", ""), samplePattern);
        }
        return pattern;
    }

    @Override
    public Optional<String> getRunId(KickoffRequest request, HashSet<String> runsWithMultipleFolders, KickoffRequest
            singleRequest, String runId) {

        String RunIDFull;
        File[] files = fastqDir.listFiles((dir1, name) -> name.startsWith(runId));

        if (files == null) {
            String message = String.format("No directories for run ID %s found.", runId);
            logWarning(message);
            return Optional.empty();
        }
        if (files.length == 0) {
            String errorMessage = String.format("Sequencing run folder not found for run id: %s in path: %s", runId,
                    fastqDir.getPath());
            throw new MappingFilePrinter.NoSequencingRunFolderFoundException(errorMessage);
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
                        "in path: %s", runId, request.getId(), fastqDir.getPath());
                throw new MappingFilePrinter.NoSequencingRunFolderFoundException(errorMessage);
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
