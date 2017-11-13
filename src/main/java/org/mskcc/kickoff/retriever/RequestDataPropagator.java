package org.mskcc.kickoff.retriever;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.util.*;

import static org.mskcc.kickoff.config.Arguments.runAsExome;

public class RequestDataPropagator implements DataPropagator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private String designFilePath;
    private String resultsPathPrefix;

    public RequestDataPropagator(String designFilePath, String resultsPathPrefix) {
        this.designFilePath = designFilePath;
        this.resultsPathPrefix = resultsPathPrefix;
    }

    @Override
    public void propagateRequestData(List<KickoffRequest> kickoffRequests) {
        for (KickoffRequest request : kickoffRequests) {
            if (request.getRequestType() == RequestType.IMPACT && runAsExome)
                request.setRequestType(RequestType.EXOME);

            Map<String, String> projectInfo = request.getProjectInfo();
            projectInfo.put(Constants.ProjectInfo.NUMBER_OF_SAMPLES, String.valueOf(request
                    .getValidUniqueCmoIdSamples(s -> !s.isPooledNormal()).size()));

            assignProjectSpecificInfo(request);
            projectInfo.put(Constants.ProjectInfo.SPECIES, String.valueOf(request.getSpecies()));

            request.setReadmeInfo(String.format("%s %s", request.getReadMe(), request.getExtraReadMeInfo()));
            addManualOverrides(request);

            if (request.getRequestType() != RequestType.RNASEQ && request.getRequestType() != RequestType.OTHER) {
                setDesignFiles(request, projectInfo);
                setAssay(request, projectInfo);
                setTumorType(request, projectInfo);
            }
            addSamplesToPatients(request);
            request.setRunNumber(getRunNumber(request));
        }
    }

    @Override
    public void setDesignFiles(KickoffRequest kickoffRequest, Map<String, String> projectInfo) {
        String[] designs = kickoffRequest.getBaitVersion().split("\\+");
        if (designs.length > 1) {
            //@TODO think about better way of indicating field not to include in request file
            projectInfo.put(Constants.ProjectInfo.DESIGN_FILE, "");
            projectInfo.put(Constants.ProjectInfo.SPIKEIN_DESIGN_FILE, "");
            projectInfo.put(Constants.ProjectInfo.ASSAY_PATH, "");
            DEV_LOGGER.info(String.format("There are more than 1 designs (bait versions) for request: %s [%s]. Design" +
                    " file, Spikein design file and Assay Path will be empty", kickoffRequest.getId(), kickoffRequest
                    .getBaitVersion()));
        } else if (kickoffRequest.getRequestType() == RequestType.IMPACT) {
            projectInfo.put(Constants.ProjectInfo.DESIGN_FILE, "");
            projectInfo.put(Constants.ProjectInfo.SPIKEIN_DESIGN_FILE, "");
            projectInfo.put(Constants.ProjectInfo.ASSAY_PATH, "");
            DEV_LOGGER.info(String.format("Request: %s is of type IMPACT. Design file, Spikein design file and Assay " +
                    "Path will be empty", kickoffRequest.getId()));
        } else {
            projectInfo.put(Constants.ProjectInfo.ASSAY_PATH, findDesignFile(kickoffRequest, kickoffRequest
                    .getBaitVersion()));
        }
    }

    @Override
    public void setTumorType(KickoffRequest kickoffRequest, Map<String, String> projectInfo) {
        HashSet<String> oncotreeCodes = new HashSet<>();
        for (Sample sample : kickoffRequest.getAllValidSamples(s -> !s.isPooledNormal()).values()) {
            String oncotreeCode = sample.get(Constants.ONCOTREE_CODE);
            if (isOncoTreeValid(oncotreeCode))
                oncotreeCodes.add(oncotreeCode);
        }

        if (oncotreeCodes.isEmpty()) {
            PM_LOGGER.log(PmLogPriority.WARNING, "I can't figure out the tumor type of this project. ");
            DEV_LOGGER.warn("I can't figure out the tumor type of this project. ");
        } else if (oncotreeCodes.size() == 1) {
            ArrayList<String> tumorTypes = new ArrayList<>(oncotreeCodes);
            projectInfo.put(Constants.ProjectInfo.TUMOR_TYPE, tumorTypes.get(0));
        } else if (oncotreeCodes.size() > 1) {
            projectInfo.put(Constants.ProjectInfo.TUMOR_TYPE, Constants.MIXED);
        } else {
            projectInfo.put(Constants.ProjectInfo.TUMOR_TYPE, Constants.NA);
        }
    }

    @Override
    public void setAssay(KickoffRequest kickoffRequest, Map<String, String> projectInfo) {
        if (!Objects.equals(kickoffRequest.getBaitVersion(), Constants.EMPTY)) {
            if (kickoffRequest.getRequestType() == RequestType.EXOME) {
                projectInfo.put(Constants.ASSAY, kickoffRequest.getBaitVersion());
            } else {
                projectInfo.put(Constants.ASSAY, "");
            }
        } else {
            projectInfo.put(Constants.ASSAY, Constants.NA);
        }
    }

    @Override
    public void addSamplesToPatients(KickoffRequest kickoffRequest) {
        for (Sample sample : kickoffRequest.getAllValidSamples().values()) {
            Map<String, String> sampleProperties = sample.getProperties();
            String patientId = sampleProperties.getOrDefault(Constants.CMO_PATIENT_ID, "");
            Patient patient = kickoffRequest.putPatientIfAbsent(patientId);
            if (!patient.isValid()) {
                String message = String.format("Cannot make smart mapping because Patient ID is emtpy or has an " +
                        "issue: %s", patientId);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
                //@TODO check how to avoid clearnig patients list
                kickoffRequest.getPatients().clear();
                return;
            }
            patient.addSample(sample);
        }
    }

    @Override
    public boolean isOncoTreeValid(String oncotreeCode) {
        return !oncotreeCode.equals(Constants.TUMOR)
                && !oncotreeCode.equals(Constants.NORMAL)
                && !oncotreeCode.equals(Constants.NA_LOWER_CASE)
                && !oncotreeCode.equals(Constants.UNKNOWN)
                && !oncotreeCode.equals(Constants.EMPTY);
    }

    @Override
    public void addManualOverrides(KickoffRequest kickoffRequest) {
        // Manual overrides are found in the readme file:
        // Current Manual overrides "OVERRIDE_BAIT_SET" - resents all bait sets (and assay) as whatever is there.
        // TODO: when list of overrides gets bigger, make it a list to search through.

        String[] lines = kickoffRequest.getReadmeInfo().split("\n");
        for (String line : lines) {
            if (line.startsWith(Constants.OVERRIDE_BAIT_SET)) {
                String[] overrideSplit = line.split(":");
                kickoffRequest.setBaitVersion(overrideSplit[overrideSplit.length - 1].trim());
                setNewBaitSet(kickoffRequest);
            }
        }
    }

    @Override
    public void setNewBaitSet(KickoffRequest kickoffRequest) {
        String newBaitset;
        String newSpikein = Constants.NA_LOWER_CASE;
        if (kickoffRequest.getBaitVersion().contains("+")) {
            String[] bv_split = kickoffRequest.getBaitVersion().split("\\+");
            newBaitset = bv_split[0];
            newSpikein = bv_split[1];
        } else {
            newBaitset = kickoffRequest.getBaitVersion();
        }

        for (Sample sample : kickoffRequest.getAllValidSamples().values()) {
            sample.put(Constants.BAIT_VERSION, kickoffRequest.getBaitVersion());
            sample.put(Constants.CAPTURE_BAIT_SET, newBaitset);
            sample.put(Constants.SPIKE_IN_GENES, newSpikein);
        }
    }

    @Override
    public void assignProjectSpecificInfo(KickoffRequest request) {
        // This will iterate the samples, grab the species, and if it is not the same and it
        // is not xenograft warning will be put.
        // If the species has been set to xenograft, it will give a warning if species is not human or xenograft
        Boolean bvChanged = false;
        for (Sample sample : request.getAllValidSamples().values()) {
            if (sample.isPooledNormal())
                continue;

            DEV_LOGGER.info(String.format("Resolving bait version for sample: %s", sample.getIgoId()));
            validateSpecies(request, sample);

            //baitVerison - sometimes bait version needs to be changed. If so, the CAPTURE_BAIT_SET must also be changed
            if (request.getRequestType() == RequestType.RNASEQ || request.getRequestType() == RequestType.OTHER) {
                String emptyBatVersion = Constants.EMPTY;
                DEV_LOGGER.info(String.format("Setting bait version to: %s for request: %s of type: %s",
                        emptyBatVersion, request.getId(), request.getRequestType().getName()));
                request.setBaitVersion(emptyBatVersion);
            } else {
                String baitVersion = sample.get(Constants.BAIT_VERSION);
                if (!StringUtils.isEmpty(baitVersion)) {
                    if (request.getRequestType() == RequestType.EXOME) {
                        // First check xenograft, if yes, then if bait version is Agilent (manual bait version for
                        // exomes) change to xenograft version of Agilent
                        if (request.getSpecies() == RequestSpecies.XENOGRAFT && baitVersion.equals(Constants
                                .MANUAL_EXOME_BAIT_VERSION_HUMAN)) {
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
                            if (!Objects.equals(bv_sp, baitVersion) && !Objects.equals(findDesignFile(request, bv_sp)
                                    , Constants.NA)) {
                                request.setBaitVersion(bv_sp);
                                baitVersion = bv_sp;
                                bvChanged = true;
                            }
                        }
                    } else {
                        if (!baitVersion.contains("+") && Objects.equals(findDesignFile(request, baitVersion),
                                Constants.NA)) {
                            Utils.setExitLater(true);
                            String message = String.format("Cannot find bait version %s design files!", baitVersion);
                            PM_LOGGER.log(Level.ERROR, message);
                            DEV_LOGGER.log(Level.ERROR, message);
                            return;
                        }
                    }
                    if (!Objects.equals(request.getBaitVersion(), baitVersion) && !Objects.equals(request
                            .getBaitVersion(), Constants.EMPTY)) {
                        String message = String.format("Request Bait version is not consistent: Current sample Bait " +
                                "verion: %s Bait version for request so far: %s", baitVersion, request.getBaitVersion
                                ());
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

    @Override
    public void validateSpecies(KickoffRequest kickoffRequest, Sample sample) {
        DEV_LOGGER.trace(String.format("Validating species for sample: %s", sample.getIgoId()));

        try {
            RequestSpecies sampleSpecies = RequestSpecies.getSpeciesByValue(sample.get(Constants.SPECIES));
            if (kickoffRequest.getSpecies() == RequestSpecies.XENOGRAFT) {
                // Xenograft projects may only have samples of sampleSpecies human or xenograft
                if (sampleSpecies != RequestSpecies.HUMAN && sampleSpecies != RequestSpecies.XENOGRAFT) {
                    Utils.setExitLater(true);
                    String message = String.format("Request species has been determined as xenograft, but this sample" +
                            " is neither xenograft or human: %s", sampleSpecies);
                    PM_LOGGER.log(Level.ERROR, message);
                    DEV_LOGGER.log(Level.ERROR, message);
                }
            } else if (kickoffRequest.getSpecies() == null) {
                kickoffRequest.setSpecies(sampleSpecies);
            } else if (kickoffRequest.getSpecies() != sampleSpecies) {
                // Requests that are not xenograft must have 100% the same sampleSpecies for each sample. If that is
                // not true, it will output issue here:
                String message = String.format("There seems to be a clash between sampleSpecies of each sample: " +
                        "Species for sample %s=%s Species for request so far=%s", sample.getProperties().get
                        (Constants.IGO_ID), sampleSpecies, kickoffRequest.getSpecies());
                Utils.setExitLater(true);
                PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                DEV_LOGGER.log(Level.ERROR, message);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about request species for " +
                    "request id: %s", kickoffRequest.getId()));
        }
    }

    @Override
    public String findDesignFile(KickoffRequest request, String assay) {
        if (request.getRequestType() == RequestType.EXOME || request.getRequestType() == RequestType.IMPACT) {
            File dir = new File(designFilePath + "/" + assay);
            DEV_LOGGER.info(String.format("Looking for design file in dir: %s", dir));
            if (dir.isDirectory()) {
                if (request.getRequestType() == RequestType.IMPACT) {
                    String berger = findDesignFileForImpact(request, assay, dir);
                    if (!StringUtils.isEmpty(berger))
                        return berger;
                } else
                    return findDesignFileForExome(dir, request.getId());
                return dir.toString();
            }
        }

        DEV_LOGGER.info(String.format("Request: %s is neither EXOME nor IMPACT thus skipping searching design file",
                request.getId()));
        return Constants.NA;
    }

    @Override
    public String findDesignFileForExome(File dir, String requestId) {
        for (File iterDirContents : Utils.getFilesInDir(dir)) {
            String exomeDesignFileExtention = "targets.ilist";
            if (iterDirContents.toString().endsWith(exomeDesignFileExtention)) {
                DEV_LOGGER.info(String.format("Found design path: %s (%s) for exome request: %s", dir,
                        exomeDesignFileExtention, requestId));
                return dir.toString();
            }
        }
        // None of the contents of this dir was a targets.ilist file. This Dir is not an exome dir
        return Constants.NA;
    }

    @Override
    public String findDesignFileForImpact(KickoffRequest request, String assay, File dir) {
        String expectedDesignFileName = String.format("%s/%s__DESIGN__LATEST.berger", dir.getAbsolutePath(), assay);
        File berger = new File(expectedDesignFileName);
        if (berger.isFile()) {
            try {
                return berger.getCanonicalPath();
            } catch (Throwable ignored) {
            }
        } else if (request.getRequestType() == RequestType.IMPACT && !runAsExome) {
            String message = String.format("Cannot find design file for assay %s. Expected file: %s doesn't exist",
                    assay, expectedDesignFileName);
            Utils.setExitLater(true);
            PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
            DEV_LOGGER.log(Level.ERROR, message);
        }

        return "";
    }

    @Override
    public int getRunNumber(KickoffRequest kickoffRequest) {
        File resultDir = new File(String.format("%s/%s/%s", resultsPathPrefix, kickoffRequest.getPi(), kickoffRequest
                .getInvest()));

        File[] files = resultDir.listFiles((dir, name) -> name.endsWith(kickoffRequest.getId().replaceFirst("^0+(?!$)" +
                "", "")));
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
        String message = "Could not determine PIPELINE RUN NUMBER from delivery directory. Setting to: 1. If this is " +
                "incorrect, email cmo-project-start@cbio.mskcc.org";
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);

        return 1;
    }
}
