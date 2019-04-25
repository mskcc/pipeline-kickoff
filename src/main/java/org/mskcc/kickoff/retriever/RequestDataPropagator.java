package org.mskcc.kickoff.retriever;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.CmoSampleInfo;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.validator.ErrorRepository;

import java.io.File;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.runAsExome;

public class RequestDataPropagator implements DataPropagator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private String designFilePath;
    private String resultsPathPrefix;
    private ErrorRepository errorRepository;
    private BiPredicate<String, String> baitSetCompatibilityPredicate;

    public RequestDataPropagator(String designFilePath,
                                 String resultsPathPrefix,
                                 ErrorRepository errorRepository,
                                 BiPredicate<String, String> baitSetCompatibilityPredicate) {
        this.designFilePath = designFilePath;
        this.resultsPathPrefix = resultsPathPrefix;
        this.errorRepository = errorRepository;
        this.baitSetCompatibilityPredicate = baitSetCompatibilityPredicate;
    }

    @Override
    public void propagateRequestData(List<KickoffRequest> kickoffRequests) {
        for (KickoffRequest request : kickoffRequests) {
            if (request.getRequestType() == RequestType.IMPACT && runAsExome)
                request.setRequestType(RequestType.EXOME);

            // @TODO refactor to use Project Info object instead of map
            Map<String, String> projectInfo = request.getProjectInfo();
            projectInfo.put(Constants.ProjectInfo.NUMBER_OF_SAMPLES, String.valueOf(request
                    .getValidUniqueCmoIdSamples(s -> !s.isPooledNormal()).size()));

            assignProjectSpecificInfo(request);
            setSpecies(request);

            request.setReadmeInfo(String.format("%s %s", request.getReadMe(), request.getExtraReadMeInfo()));
            addManualOverrides(request);

            if (request.getRequestType() != RequestType.RNASEQ && request.getRequestType() != RequestType.OTHER) {
                setDesignFiles(request, projectInfo);
                setAssay(request, projectInfo);
                setTumorType(request, projectInfo);
            }
            addSamplesToPatients(request);
            request.setRunNumber(getRunNumber(request));
            request.setReadmeInfo(String.format("%s %s", request.getReadMe(), request.getExtraReadMeInfo()));
        }
    }

    private void setSpecies(KickoffRequest request) {
        Set<Set<RequestSpecies>> species = getSpecies(request);
        request.setSpeciesSet(species);
        request.getProjectInfo().put(Constants.ProjectInfo.SPECIES, getSpeciesConcat(species));
    }

    private String getSpeciesConcat(Set<Set<RequestSpecies>> species) {
        Set<String> uniqueSpecies = species.stream()
                .map(s -> s.stream()
                        .map(s1 -> s1.getValue())
                        .collect(Collectors.joining("+")))
                .collect(Collectors.toSet());

        return String.join(",", uniqueSpecies);
    }

    private Set<Set<RequestSpecies>> getSpecies(KickoffRequest request) {
        return request.getAllValidSamples().values().stream()
                .map(s -> s.get(Constants.SPECIES))
                .filter(s -> !StringUtils.isEmpty(s))
                .map(s -> Arrays.stream(s.split(","))
                        .map(s1 -> String.valueOf(s1))
                        .map(s1 -> RequestSpecies.getSpeciesByValue(s1))
                        .collect(Collectors.toSet()))
                .distinct()
                .collect(Collectors.toSet());
    }

    @Override
    public void setDesignFiles(KickoffRequest kickoffRequest, Map<String, String> projectInfo) {
        String[] designs = kickoffRequest.getBaitVersion().split("\\+");
        if (designs.length > 1) {
            //@TODO think about better way of indicating field not to include in request file
            projectInfo.put(Constants.ProjectInfo.DESIGN_FILE, "");
            projectInfo.put(Constants.ProjectInfo.SPIKEIN_DESIGN_FILE, "");
            DEV_LOGGER.info(String.format("There are more than 1 designs (bait versions) for request: %s [%s]. Design" +
                    " file, Spikein design file and Assay Path will be empty", kickoffRequest.getId(), kickoffRequest
                    .getBaitVersion()));
        } else if (kickoffRequest.getRequestType() == RequestType.IMPACT) {
            projectInfo.put(Constants.ProjectInfo.DESIGN_FILE, "");
            projectInfo.put(Constants.ProjectInfo.SPIKEIN_DESIGN_FILE, "");
            DEV_LOGGER.info(String.format("Request: %s is of type IMPACT. Design file, Spikein design file and Assay " +
                    "Path will be empty", kickoffRequest.getId()));
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
            PM_LOGGER.log(PmLogPriority.WARNING, String.format("I can't figure out the tumor type for request [%s]: no valid Oncotree Code found!", kickoffRequest.getId()));
            DEV_LOGGER.warn(String.format("I can't figure out the tumor type for request [%s]: no valid Oncotree Code found!", kickoffRequest.getId()));
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
        if (StringUtils.isEmpty(kickoffRequest.getBaitVersion())) {
            projectInfo.put(Constants.ASSAY, "");
        } else if (Objects.equals(kickoffRequest.getBaitVersion(), Constants.EMPTY)) {
            projectInfo.put(Constants.ASSAY, Constants.NA);
        } else {
            projectInfo.put(Constants.ASSAY, kickoffRequest.getBaitVersion());
        }
    }

    @Override
    public void addSamplesToPatients(KickoffRequest kickoffRequest) {
        Map<String, String> projectInfo = kickoffRequest.getProjectInfo();

        String patientFieldKey = getPatientFieldKey(kickoffRequest);
        if (Constants.NA.equals(patientFieldKey)) {
            String message = "PM is " + projectInfo.get(Constants.ProjectInfo.PROJECT_MANAGER) +
                    ", but at least one sample's CMO Patient ID or Investigator Patient ID has empty or MRN_REDACTED value. ";
            PM_LOGGER.log(PmLogPriority.WARNING, message);
            DEV_LOGGER.warn(message);
            errorRepository.add(new GenerationError(message, ErrorCode.EMPTY_PATIENT));
            return;
        }

        DEV_LOGGER.info("PM is " + projectInfo.get(Constants.ProjectInfo.PROJECT_MANAGER) +
                ", using patient id from: " + patientFieldKey);

        Map<String, Sample> sampleMap = kickoffRequest.getAllValidSamples();
        for (Sample sample : sampleMap.values()) {
            Map<String, Object> sampleProperties = sample.getCmoSampleInfo().getFields();
            String patientId = "";
            if (sample.isPooledNormal()) {
                patientId = sample.getProperties().getOrDefault(
                        CmoSampleInfo.CMO_PATIENT_ID.equals(patientFieldKey) ?
                                Constants.CMO_PATIENT_ID : Constants.INVESTIGATOR_PATIENT_ID, "");
            } else {
                patientId = (String) sampleProperties.getOrDefault(patientFieldKey, "");
            }
            Patient patient = kickoffRequest.putPatientIfAbsent(patientId);
            if (!sample.isPooledNormal() && !isValidPatientId(patientId)) {
                String message = String.format("Patient ID for sample %s is empty or has an issue: %s",
                        sample.getIgoId(), patientId);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
                kickoffRequest.getPatients().clear();
                errorRepository.add(new GenerationError(message, ErrorCode.EMPTY_PATIENT));
                return;
            }
            patient.addSample(sample);
        }
    }

    private String getPatientFieldKey(KickoffRequest request) {

        String pm = request.getProjectInfo().get(Constants.ProjectInfo.PROJECT_MANAGER);
        if (!Utils.isCmoSideProject(pm)) {
            return CmoSampleInfo.CMO_PATIENT_ID;
        }

        Map<String, Sample> sampleMap = request.getValidNonPooledNormalSamples();

        boolean cmoPatientIdBlank = sampleMap.values().stream()
                .map(s -> s.getCmoSampleInfo().getCmoPatientId())
                .anyMatch(s -> StringUtils.isBlank(s) || Constants.EMPTY.equals(s));

        boolean investPatientIdBlank = sampleMap.values().stream()
                .map(s -> s.getCmoSampleInfo().getPatientId())
                .anyMatch(s -> StringUtils.isBlank(s)
                        || Constants.EMPTY.equals(s)
                        || s.equals(Constants.MRN_REDACTED));

        if (!cmoPatientIdBlank) {
            return CmoSampleInfo.CMO_PATIENT_ID;
        } else if (!investPatientIdBlank) {
            return CmoSampleInfo.PATIENT_ID;
        } else {
            return Constants.NA;
        }
    }

    private boolean isValidPatientId(String patientId) {
        return StringUtils.isNotBlank(patientId) && !patientId.startsWith("#");
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

            //baitVerison - sometimes bait version needs to be changed. If so, the CAPTURE_BAIT_SET must also be changed
            if (request.getRequestType() == RequestType.RNASEQ || request.getRequestType() == RequestType.OTHER) {
                String emptyBaitVersion = Constants.EMPTY;
                DEV_LOGGER.info(String.format("Setting bait version to: %s for request: %s of type: %s",
                        emptyBaitVersion, request.getId(), request.getRequestType().getName()));
                request.setBaitVersion(emptyBaitVersion);
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
                    if (!isBaitSetCompatible(request, baitVersion)) {
                        String message = String.format("Inconsistent bait version: " +
                                        "bait version for current sample %s: %s, bait version for request so far: %s",
                                sample.getIgoId(), baitVersion, request.getBaitVersion());
                        Utils.setExitLater(true);
                        PM_LOGGER.log(Level.ERROR, message);
                        DEV_LOGGER.log(Level.ERROR, message);
                        errorRepository.add(new GenerationError(message, ErrorCode.BAIT_SET_NOT_COMPATIBLE));
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

    private boolean isBaitSetCompatible(KickoffRequest request, String baitVersion) {
        return baitSetCompatibilityPredicate.test(request.getBaitVersion(), baitVersion) || Objects.equals(request
                .getBaitVersion(), Constants.EMPTY);
    }

    @Override
    public String findDesignFile(KickoffRequest request, String assay) {
        if (request.getRequestType() == RequestType.EXOME || request.getRequestType() == RequestType.IMPACT) {
            File dir = new File(designFilePath + "/" + assay);
            DEV_LOGGER.info(String.format("Looking for design file in dir: %s", dir));
            if (dir.isDirectory()) {
                if (request.getRequestType() == RequestType.IMPACT) {
                    String berger = findDesignFileForImpact(request, assay, dir);
                    if (!StringUtils.isEmpty(berger)) {
                        return berger;
                    }
                } else {
                    return findDesignFileForExome(dir, request.getId());
                }
                return dir.toString();
            } else {
                DEV_LOGGER.info(String.format("Request: %s is %s, but design file is not found.",
                        request.getId(), request.getRequestType()));
            }
        } else {
            DEV_LOGGER.info(String.format("Request: %s is neither EXOME nor IMPACT thus skipping searching design file",
                    request.getId()));
        }

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
            if (files2 != null && files2.length > 0) {
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