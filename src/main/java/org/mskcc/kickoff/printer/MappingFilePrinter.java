package org.mskcc.kickoff.printer;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.domain.Sample;
import org.mskcc.kickoff.lims.SampleInfo;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.util.*;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.config.Arguments.shiny;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

public class MappingFilePrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);

    private static final String NORMAL_MAPPING_FILE_NAME = "sample_mapping.txt";
    private static final String ERROR_MAPPING_FILE_NAME = "sample_mapping.error";

    private final Set<SampleRun> sampleRuns = new LinkedHashSet<>();

    @Value("${fastq_path}")
    private String fastq_path;

    @Override
    public void print(Request request) {
        Map<String, String> sampleRenamesAndSwaps = SampleInfo.getSampleRenames();

        File mappingFile = null;

        try {
            HashSet<String> runsWithMultipleFolders = new HashSet<>();

            String mappingFileContents = "";
            String requestID = request.getId();
            for (Sample sample : request.getAllValidSamples().values()) {
                for (String runId : sample.getValidRunIds()) {
                    sampleRuns.add(new SampleRun(sample, runId));
                }
            }

            for (SampleRun sampleRun : sampleRuns) {
                HashSet<String> sample_pattern = new HashSet<>();
                Sample sample = sampleRun.getSample();
                String sampleId = sample.getCmoSampleId();
                if (!StringUtils.isEmpty(sample.getAlias())) {
                    ArrayList<String> aliasSampleNames = new ArrayList<>(Arrays.asList(sample.getAlias().split(";")));
                    for (String aliasName : aliasSampleNames) {
                        String message = String.format("Sample %s has alias %s", sample, aliasName);
                        logWarning(message);
                        sample_pattern.add(aliasName.replaceAll("[_-]", "[-_]"));
                    }
                } else {
                    sample_pattern.add(sampleId.replaceAll("[_-]", "[-_]"));
                }

                // Here Find the RUN ID. Iterate through each directory in fastq_path so I can search through each FASTQ directory
                // Search each /FASTQ/ directory for directories that start with "RUN_ID"
                // Take the newest one?

                // This takes the best guess for the run id, and has bash fill out the missing parts!
                final String runId = sampleRun.getRunId();
                String RunIDFull;

                //Iterate through fastq_path
                File dir = new File(fastq_path + "/hiseq/FASTQ/");

                File[] files = dir.listFiles((dir1, name) -> name.startsWith(runId));

                // find out how many run IDs came back
                if (files == null) {
                    String message = String.format("No directories for run ID %s found.", runId);
                    logWarning(message);
                    continue;
                }
                if (files.length == 0) {
                    String message = String.format("Could not find sequencing run folder for Run ID: %s", runId);
                    Utils.setExitLater(true);
                    PM_LOGGER.log(Level.ERROR, message);
                    DEV_LOGGER.log(Level.ERROR, message);
                    request.setMappingIssue(true);
                    return;
                } else if (files.length > 1) {
                    // Here I will remove any directories that do NOT have the project as a folder in the directory.
                    ArrayList<File> runsWithProjectDir = new ArrayList<>();
                    for (File runDir : files) {
                        File requestPath = new File(runDir.getAbsoluteFile().toString() + "/Project_" + requestID);
                        if (requestPath.exists() && requestPath.isDirectory()) {
                            runsWithProjectDir.add(runDir);
                        }
                    }
                    files = runsWithProjectDir.toArray(new File[runsWithProjectDir.size()]);
                    if (files == null) {
                        String message = "No run ids with request id found.";
                        logWarning(message);
                        continue;
                    }
                    if (files.length == 0) {
                        String message = String.format("Could not find sequencing run folder that also contains request %s for Run ID: %s", requestID, runId);
                        Utils.setExitLater(true);
                        PM_LOGGER.log(Level.ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                        request.setMappingIssue(true);
                        return;
                    } else if (files.length > 1) {
                        //@TODO check if the newest folder in taken into consideration
                        Arrays.sort(files);
                        String foundFiles = StringUtils.join(files, ", ");
                        if (!runsWithMultipleFolders.contains(runId)) {
                            String message = String.format("More than one sequencing run folder found for Run ID %s: %s I will be picking the newest folder.", runId, foundFiles);
                            logWarning(message);
                            runsWithMultipleFolders.add(runId);
                        }
                        RunIDFull = files[files.length - 1].getAbsoluteFile().getName().toString();
                    } else {
                        RunIDFull = files[0].getAbsoluteFile().getName();
                    }
                } else {
                    RunIDFull = files[0].getAbsoluteFile().getName();
                }

                // Grab RUN ID, save it for the request file.
                request.addRunIDlist(RunIDFull);

                for (String S_Pattern : sample_pattern) {
                    String pattern;
                    if (!isPooledNormalSample(sample)) {
                        pattern = dir.toString() + "/" + RunIDFull + "*/Proj*" + requestID.replaceFirst("^0+(?!$)", "") + "/Sample_" + S_Pattern;
                    } else {
                        pattern = dir.toString() + "/" + RunIDFull + "*/Proj*" + "/Sample_" + S_Pattern + "*";
                    }

                    String cmd = "ls -d " + pattern;

                    Process pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                    pr.waitFor();

                    //@TODO move to atnoher place, manifest file depends on new mapping so it has to be done before printing any files
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
                                String message = String.format("Error while trying to find fastq Directory for %s it is probably mispelled, or has an alias.", sample);
                                Utils.setExitLater(true);
                                PM_LOGGER.log(Level.ERROR, message);
                                DEV_LOGGER.log(Level.ERROR, message);
                                BufferedReader bufE = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                                while (bufE.ready()) {
                                    DEV_LOGGER.error(bufE.readLine());
                                }
                                request.setMappingIssue(true);
                                continue;
                            } else {
                                request.setNewMappingScheme(1);
                            }
                        } else {
                            // this working means that I have to change the cmo sample id to have the seq iD.
                            if (!seqID.equals(igoID)) {
                                String manifestSampleID = sample.get(Constants.MANIFEST_SAMPLE_ID).replace("IGO_" + igoID, "IGO_" + seqID);
                                sample.put(Constants.MANIFEST_SAMPLE_ID, manifestSampleID);
                            }
                            request.setNewMappingScheme(1);
                        }
                    }
                    String sampleFqPath = StringUtils.chomp(IOUtils.toString(pr.getInputStream()));

                    if (sampleFqPath.isEmpty()) {
                        continue;
                    }

                    String[] paths = sampleFqPath.split("\n");

                    // Find out if this is single end or paired end by looking inside the directory for a R2_001.fastq.gz
                    for (String path : paths) {
                        if ((sampleId.contains("POOLEDNORMAL") && (sampleId.contains("FFPE") || sampleId.contains("FROZEN"))) && !path.matches("(.*)IGO_" + request.getBaitVersion().toUpperCase() + "_[ATCG](.*)")) {
                            continue;
                        }

                        String paired = "SE";
                        File sampleFq = new File(path);
                        File[] listOfFiles = sampleFq.listFiles();
                        for (File f1 : listOfFiles) {
                            if (f1.getName().endsWith("_R2_001.fastq.gz")) {
                                paired = "PE";
                                break;
                            }
                        }

                        // Confirm there is a SampleSheet.csv in the path:
                        File samp_sheet = new File(path + "/SampleSheet.csv");
                        if (!samp_sheet.isFile() && request.getRequestType().equals(Constants.IMPACT)) {
                            String message = String.format("Sample %s from run %s does not have a sample sheet in the sample directory. This will not pass the validator.", sample, RunIDFull);
                            if (shiny) {
                                PM_LOGGER.error(message);
                            } else {
                                PM_LOGGER.log(PmLogPriority.WARNING, message);
                            }
                            DEV_LOGGER.warn(message);
                        }


                        // FLATTEN ALL sample ids herei:
                        String sampleName = sampleNormalization(sampleId);
                        if (sampleRenamesAndSwaps.containsKey(sampleId)) {
                            sampleName = sampleNormalization(sampleRenamesAndSwaps.get(sampleId));
                        }

                        mappingFileContents += String.format("_1\t%s\t%s\t%s\t%s\n", sampleName, RunIDFull, path, paired);
                    }
                }
            }

            if (mappingFileContents.length() > 0) {
                mappingFileContents = filterToAscii(mappingFileContents);
                String mappingFileName = shouldOutputErrorFile(request) ? ERROR_MAPPING_FILE_NAME : NORMAL_MAPPING_FILE_NAME;
                mappingFile = new File(String.format("%s/%s_%s", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(requestID), mappingFileName));
                PrintWriter pW = new PrintWriter(new FileWriter(mappingFile, false), false);
                filesCreated.add(mappingFile);
                pW.write(mappingFileContents);
                pW.close();
            }

        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating mapping file: %s", mappingFile), e);
        }
    }

    private boolean shouldOutputErrorFile(Request request) {
        return Utils.isExitLater()
                && request.isMappingIssue()
                && !krista
                && !request.isInnovationProject()
                && !Objects.equals(request.getRequestType(), Constants.RNASEQ)
                && !Objects.equals(request.getRequestType(), Constants.OTHER);
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
    public boolean shouldPrint(Request request) {
        return ((request.getRequestType().equals(Constants.RNASEQ) || request.getRequestType().equals(Constants.OTHER))
                && !request.isForced()
                && !request.isManualDemux()
        )
                || !request.isForced();
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

            if (!sample.getCmoSampleId().equals(sampleRun.sample.getCmoSampleId())) return false;
            return runId.equals(sampleRun.runId);
        }

        @Override
        public int hashCode() {
            int result = sample.getCmoSampleId().hashCode();
            result = 31 * result + runId.hashCode();
            return result;
        }
    }
}
