package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.FilePermissionConfigurator;
import org.mskcc.kickoff.config.LogConfigurator;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.lims.QueryImpactProjectInfo;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.util.*;

import static org.mskcc.kickoff.config.Arguments.*;

public class FilesGenerator implements ManifestGenerator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    private RequestProxy requestProxy;
    @Autowired
    private QueryImpactProjectInfo queryImpactProjectInfo;

    @Value("${draftProjectFilePath}")
    private String draftProjectFilePath;

    @Value("${designFilePath}")
    private String designFilePath;

    @Value("${resultsPathPrefix}")
    private String resultsPathPrefix;

    @Autowired
    private LogConfigurator logConfigurator;

    @Autowired
    private ProjectNameValidator projectNameValidator;

    @Autowired
    private OutputFilesPrinter manifestOutputFilesPrinter;

    @Autowired
    private FilesArchiver filesArchiver;

    @Autowired
    private RequestValidator requestValidator;

    @Override
    public void generate() {
        logConfigurator.configureDevLog();

        DEV_LOGGER.info("Received program arguments: " + toPrintable());

        logConfigurator.configureProjectLog(outdir);

        if (projectNameValidator.isValid(Arguments.project)) {
            List<Request> requests = requestProxy.getRequests(Arguments.project);

            validateRequestExists(requests);

            for (Request request : requests) {
                request.setOutputPath(outdir);
                generate(request);
            }
        }
    }

    private void validateRequestExists(List<Request> requests) {
        if (requests.size() == 0) {
            String message = String.format("No matching requests for request id: %s", Arguments.project);
            PM_LOGGER.info(message);
            throw new RuntimeException(message);
        }
    }

    private void generate(Request request) {
        try {
            requestValidator.validate(request);

            if (shiny && request.getRequestType().equals(Constants.RNASEQ)) {
                DEV_LOGGER.error("This is an RNASeq project, and you cannot grab this information yet via Shiny");
                return;
            }

            requestValidator.validateSampleUniqueness(request);

            if (Objects.equals(request.getRequestType(), Constants.IMPACT) && runAsExome)
                request.setRequestType(Constants.EXOME);

            requestValidator.validateSamplesExist(request);

            //@TODO move to the begginig , to initialization
            Map<String, String> projectInfo = getProjectInfo(request);
            request.setProjectInfo(projectInfo);

            if (!Objects.equals(request.getPi(), Constants.NULL) && !Objects.equals(request.getInvest(), Constants.NULL)) {
                int runNum = getRunNumber(request);
                request.setRunNumber(runNum);
                if (!request.isForced()) {
                    request.archiveFilesToOld();
                }
            }

            projectInfo.put("NumberOfSamples", String.valueOf(request.getValidUniqueCmoIdSamples(s -> !s.isPooledNormal()).size()));

            assignProjectSpecificInfo(request);
            projectInfo.put("Species", String.valueOf(request.getSpecies()));

            request.setReadmeInfo(String.format("%s %s", request.getReadMe(), request.getExtraReadMeInfo()));
            getManualOverrides(request);


            if (request.getRequestType().equals(Constants.RNASEQ) || request.getRequestType().equals(Constants.OTHER)) {
                String message = String.format("Path: %s", request.getOutputPath());
                PM_LOGGER.info(message);
                DEV_LOGGER.info(message);

                if (!request.isForced() && request.isManualDemux()) {
                    String message1 = "Manual demux performed. I will not output maping file";
                    PM_LOGGER.log(PmLogPriority.WARNING, message1);
                    DEV_LOGGER.warn(message1);
                }
            } else {
                File outputPath = new File(request.getOutputPath());
                outputPath.mkdirs();

                if (request.getRequestType().equals(Constants.RNASEQ)) {
                    projectInfo.put("DesignFile", Constants.NA);
                    projectInfo.put(Constants.ASSAY, Constants.NA);
                    projectInfo.put("TumorType", Constants.NA);
                } else {
                    String[] designs = request.getBaitVersion().split("\\+");
                    if (designs.length > 1) {
                        //@TODO think about better way of indicating field not no include in request file
                        projectInfo.put("DesignFile", "");
                        projectInfo.put("SpikeinDesignFile", "");
                        projectInfo.put("AssayPath", "");
                    } else if (request.getRequestType().equals(Constants.IMPACT)) {
                        projectInfo.put("DesignFile", "");
                        projectInfo.put("SpikeinDesignFile", "");
                        projectInfo.put("AssayPath", "");
                    } else {
                        projectInfo.put("AssayPath", findDesignFile(request, request.getBaitVersion()));
                    }
                    if (!Objects.equals(request.getBaitVersion(), Constants.EMPTY)) {
                        if (Objects.equals(request.getRequestType(), Constants.EXOME)) {
                            projectInfo.put(Constants.ASSAY, request.getBaitVersion());
                        } else {
                            projectInfo.put(Constants.ASSAY, "");
                        }
                    } else {
                        projectInfo.put(Constants.ASSAY, Constants.NA);
                    }

                    HashSet<String> oncotreeCodes = new HashSet<>();
                    for (Sample sample : request.getAllValidSamples().values()) {
                        String oncotreeCode = sample.get(Constants.ONCOTREE_CODE);
                        if (isOncoTreeValid(oncotreeCode))
                            oncotreeCodes.add(oncotreeCode);
                    }

                    if (oncotreeCodes.isEmpty()) {
                        PM_LOGGER.log(PmLogPriority.WARNING, "I can't figure out the tumor type of this project. ");
                        DEV_LOGGER.warn("I can't figure out the tumor type of this project. ");
                    } else if (oncotreeCodes.size() == 1) {
                        ArrayList<String> tumorTypes = new ArrayList<>(oncotreeCodes);
                        projectInfo.put("TumorType", tumorTypes.get(0));
                    } else if (oncotreeCodes.size() > 1) {
                        projectInfo.put("TumorType", "mixed");
                    } else {
                        projectInfo.put("TumorType", Constants.NA);
                    }
                }
            }

            addSamplesToPatients(request);
            manifestOutputFilesPrinter.print(request);
            filesArchiver.archive(request);
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        } finally {
            File f = new File(request.getOutputPath());
            FilePermissionConfigurator.setPermissions(f);
        }
    }

    private void addSamplesToPatients(Request request) {
        for (Sample sample : request.getAllValidSamples().values()) {
            Map<String, String> sampleProperties = sample.getProperties();
            String patientId = sampleProperties.getOrDefault(Constants.CMO_PATIENT_ID, "");
            Patient patient = request.putPatientIfAbsent(patientId);
            if (!patient.isValid()) {
                String message = String.format("Cannot make smart mapping because Patient ID is emtpy or has an issue: %s", patientId);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
                //@TODO check how to avoid clearnig patients list
                request.getPatients().clear();
                return;
            }
            patient.addSample(sample);
        }
    }

    private boolean isOncoTreeValid(String oncotreeCode) {
        return !StringUtils.isEmpty(oncotreeCode)
                && !Objects.equals(oncotreeCode, Constants.TUMOR)
                && !Objects.equals(oncotreeCode, Constants.NORMAL)
                && !Objects.equals(oncotreeCode, Constants.NA_LOWER_CASE)
                && !Objects.equals(oncotreeCode, Constants.UNKNOWN)
                && !Objects.equals(oncotreeCode, Constants.EMPTY);
    }

    private void getManualOverrides(Request request) {
        // Manual overrides are found in the readme file:
        // Current Manual overrides "OVERRIDE_BAIT_SET" - resents all bait sets (and assay) as whatever is there.
        // TODO: when list of overrides gets bigger, make it a list to search through.

        String[] lines = request.getReadmeInfo().split("\n");
        for (String line : lines) {
            if (line.startsWith(Constants.OVERRIDE_BAIT_SET)) {
                String[] overrideSplit = line.split(":");
                request.setBaitVersion(overrideSplit[overrideSplit.length - 1].trim());
                setNewBaitSet(request);
            }
        }
    }

    private void setNewBaitSet(Request request) {
        String newBaitset;
        String newSpikein = Constants.NA_LOWER_CASE;
        if (request.getBaitVersion().contains("+")) {
            String[] bv_split = request.getBaitVersion().split("\\+");
            newBaitset = bv_split[0];
            newSpikein = bv_split[1];
        } else {
            newBaitset = request.getBaitVersion();
        }

        for (Sample sample : request.getAllValidSamples().values()) {
            sample.put(Constants.BAIT_VERSION, request.getBaitVersion());
            sample.put(Constants.CAPTURE_BAIT_SET, newBaitset);
            sample.put(Constants.SPIKE_IN_GENES, newSpikein);
        }
    }

    private Map<String, String> getProjectInfo(Request request) {
        Map<String, String> projectInfo = queryImpactProjectInfo.queryProjectInfo(request);
        request.setPi(queryImpactProjectInfo.getPI().split("@")[0]);
        request.setInvest(queryImpactProjectInfo.getInvest().split("@")[0]);

        return projectInfo;
    }

    private void assignProjectSpecificInfo(Request request) {
        // This will iterate the samples, grab the species, and if it is not the same and it
        // is not xenograft warning will be put.
        // If the species has been set to xenograft, it will give a warning if species is not human or xenograft
        Boolean bvChanged = false;
        for (Sample sample : request.getAllValidSamples().values()) {
            //@TODO check if can use sample.isPooledNormal()
            if (sample.isPooledNormal()) {
                continue;
            }

            validateSpecies(request, sample);

            //baitVerison - sometimes bait version needs to be changed. If so, the CAPTURE_BAIT_SET must also be changed
            if (Objects.equals(request.getRequestType(), Constants.RNASEQ) || Objects.equals(request.getRequestType(), Constants.OTHER)) {
                request.setBaitVersion(Constants.EMPTY);
            } else {
                String baitVersion = sample.get(Constants.BAIT_VERSION);
                if (!StringUtils.isEmpty(baitVersion)) {
                    if (Objects.equals(request.getRequestType(), Constants.EXOME)) {
                        // First check xenograft, if yes, then if bait version is Agilent (manual bait version for exomes) change to xenograft version of Agilent
                        if (request.getSpecies() == RequestSpecies.XENOGRAFT && baitVersion.equals(Constants.MANUAL_EXOME_BAIT_VERSION_HUMAN)) {
                            baitVersion = Constants.MANUAL_EXOME_XENOGRAFT_BAIT_VERSION_HUMAN_MOUSE;
                            bvChanged = true;
                        }
                        String bv_sp = baitVersion;
                        if (Objects.equals(findDesignFile(request, baitVersion), Constants.NA)) {
                            // Add species to end of baitVersion
                            String humanAbrevSpecies = Constants.HUMAN_ABREV;
                            String mouseAbrevSpecies = Constants.MOUSE_ABREV;
                            if (request.getSpecies() == RequestSpecies.HUMAN) {
                                bv_sp = baitVersion + "_" + humanAbrevSpecies;
                            } else if (request.getSpecies() == RequestSpecies.MOUSE) {
                                bv_sp = baitVersion + "_" + mouseAbrevSpecies;
                            } else if (request.getSpecies() == RequestSpecies.XENOGRAFT) {
                                bv_sp = baitVersion + "_" + humanAbrevSpecies + "_" + mouseAbrevSpecies;
                            }
                            if (!Objects.equals(bv_sp, baitVersion) && !Objects.equals(findDesignFile(request, bv_sp), Constants.NA)) {
                                request.setBaitVersion(bv_sp);
                                baitVersion = bv_sp;
                                bvChanged = true;
                            }
                        }
                    } else {
                        if (!baitVersion.contains("+") && Objects.equals(findDesignFile(request, baitVersion), Constants.NA)) {
                            Utils.setExitLater(true);
                            String message = String.format("Cannot find bait version %s design files!", baitVersion);
                            PM_LOGGER.log(Level.ERROR, message);
                            DEV_LOGGER.log(Level.ERROR, message);
                            return;
                        }
                    }
                    if (!Objects.equals(request.getBaitVersion(), baitVersion) && !Objects.equals(request.getBaitVersion(), Constants.EMPTY)) {
                        String message = String.format("Request Bait version is not consistent: Current sample Bait verion: %s Bait version for request so far: %s", baitVersion, request.getBaitVersion());
                        Utils.setExitLater(true);
                        PM_LOGGER.log(Level.ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                    } else if (Objects.equals(request.getBaitVersion(), Constants.EMPTY)) {
                        request.setBaitVersion(baitVersion);
                    }
                }
            }
        }
        if (bvChanged) {
            setNewBaitSet(request);
        }
    }

    private void validateSpecies(Request request, Sample sample) {
        try {
            RequestSpecies sampleSpecies = RequestSpecies.getSpeciesByValue(sample.getProperties().get(Constants.SPECIES));
            if (request.getSpecies() == RequestSpecies.XENOGRAFT) {
                // Xenograft projects may only have samples of sampleSpecies human or xenograft
                if (sampleSpecies != RequestSpecies.HUMAN && sampleSpecies != RequestSpecies.XENOGRAFT) {
                    Utils.setExitLater(true);
                    String message = String.format("Request species has been determined as xenograft, but this sample is neither xenograft or human: %s", sampleSpecies);
                    PM_LOGGER.log(Level.ERROR, message);
                    DEV_LOGGER.log(Level.ERROR, message);
                }
            } else if (request.getSpecies() == RequestSpecies.EMPTY) {
                request.setSpecies(sampleSpecies);
            } else if (request.getSpecies() != sampleSpecies) {
                // Requests that are not xenograft must have 100% the same sampleSpecies for each sample. If that is not true, it will output issue here:
                String message = String.format("There seems to be a clash between sampleSpecies of each sample: Species for sample %s=%s Species for request so far=%s", sample.getProperties().get(Constants.IGO_ID), sampleSpecies, request.getSpecies());
                Utils.setExitLater(true);
                PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                DEV_LOGGER.log(Level.ERROR, message);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about request species for request id: %s", Arguments.project));
        }
    }

    private String findDesignFile(Request request, String assay) {
        if (Objects.equals(request.getRequestType(), Constants.IMPACT) || Objects.equals(request.getRequestType(), Constants.EXOME)) {
            File dir = new File(designFilePath + "/" + assay);
            if (dir.isDirectory()) {
                if (Objects.equals(request.getRequestType(), Constants.IMPACT)) {
                    File berger = new File(String.format("%s/%s__DESIGN__LATEST.berger", dir.getAbsolutePath(), assay));
                    if (berger.isFile()) {
                        try {
                            return berger.getCanonicalPath();
                        } catch (Throwable ignored) {
                        }
                    } else if (Objects.equals(request.getRequestType(), Constants.IMPACT) && !runAsExome) {
                        String message = String.format("Cannot find design file for assay %s", assay);
                        Utils.setExitLater(true);
                        PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                    }
                } else { // exome
                    for (File iterDirContents : Utils.getFilesInDir(dir)) {
                        if (iterDirContents.toString().endsWith("targets.ilist")) {
                            return dir.toString();
                        }
                    }
                    // None of the contents of this dir was a targets.ilist file. This Dir is not an exome dir
                    return Constants.NA;
                }
                return dir.toString();
            }
        }
        return Constants.NA;
    }

    private int getRunNumber(Request request) {
        File resultDir = new File(String.format("%s/%s/%s", resultsPathPrefix, request.getPi(), request.getInvest()));

        File[] files = resultDir.listFiles((dir, name) -> name.endsWith(request.getId().replaceFirst("^0+(?!$)", "")));
        if (files != null && files.length == 1) {
            File projectResultDir = files[0];
            File[] files2 = projectResultDir.listFiles((dir, name) -> name.startsWith("r_"));
            if (files2.length > 0) {
                int[] runs = new int[files2.length];
                int index = 0;
                for (File f : files2) {
                    int pastRuns = Integer.parseInt(f.getName().replaceFirst("^r_", ""));
                    runs[index] = pastRuns;
                    index++;
                }

                Arrays.sort(runs);

                return runs[runs.length - 1] + 1;
            }
        }
        String message = "Could not determine PIPELINE RUN NUMBER from delivery directory. Setting to: 1. If this is incorrect, email cmo-project-start@cbio.mskcc.org";
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);

        return 1;
    }
}
