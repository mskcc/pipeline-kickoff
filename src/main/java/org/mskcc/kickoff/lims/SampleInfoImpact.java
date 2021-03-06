package org.mskcc.kickoff.lims;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.retriever.NimblegenResolver;
import org.mskcc.kickoff.retriever.RecordNimblegenResolver;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.util.Constants;
import org.mskcc.util.VeloxConstants;

import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mskcc.util.VeloxConstants.CMO_SAMPLE_CLASS;
import static org.mskcc.util.VeloxConstants.TUMOR_OR_NORMAL;

/**
 * SampleInfoImpact<br>Purpose: this is a class extending SampleInfo so that we can grab all necessary information from
 * extra data records as needed for IMPACT/dmp pipeline
 **/
public class SampleInfoImpact extends SampleInfo {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private static final HashSet<String> poolNameList = new HashSet<>();
    private static final DecimalFormat df = new DecimalFormat("#.##");
    private static final String SEQ_RUN_FOLDER_PATTERN = "[A-Za-z0-9]+_([A-Za-z0-9]+_[A-Za-z0-9]+)_[A-Za-z0-9]+";
    private static SetMultimap<DataRecord, String> pooledNormals = HashMultimap.create();
    // Consensus BaitSet
    private static String ConsensusBaitSet;
    private static String ConsensusSpikeIn;
    private final NimblegenResolver nimblegenResolver;
    protected LocalDateTime kapaProtocolStartDate;
    String LIBRARY_INPUT;// = "#EMPTY";
    String LIBRARY_YIELD; // = "#EMPTY";
    String CAPTURE_BAIT_SET; // = "#EMPTY";
    String CAPTURE_INPUT; // = "#EMPTY";
    String BAIT_VERSION; // = "#EMPTY";
    private String CMO_PATIENT_ID;
    private String SEX; // = "Unknown";
    private String SPECIMEN_COLLECTION_YEAR; // ="000";
    private String ONCOTREE_CODE; // = "#EMPTY";
    private String TISSUE_SITE; // = "na";
    private String CAPTURE_CONCENTRATION; // = "#EMPTY";
    private String CAPTURE_NAME; // = "#EMPTY";
    private String SPIKE_IN_GENES; // = "na";
    private ErrorRepository errorRepository;

    /**
     * This is the method that CreateManifestSheet calls.<br>First it looks at parent class's method, then uses new
     * method "getSpreadOutInfo" and "addPoolNormalDefaults"
     *
     * @see SampleInfo#SampleInfo
     * @see #getSpreadOutInfo
     * @see #addPooledNormalDefaults
     **/
    public SampleInfoImpact(User apiUser,
                            DataRecordManager drm,
                            DataRecord sampleRecord,
                            KickoffRequest kickoffRequest,
                            Sample sample,
                            LocalDateTime kapaProtocolStartDate,
                            NimblegenResolver nimblegenResolver,
                            ErrorRepository errorRepository) {
        super(apiUser, drm, sampleRecord, kickoffRequest, sample);
        this.kapaProtocolStartDate = kapaProtocolStartDate;
        this.nimblegenResolver = nimblegenResolver;
        this.errorRepository = errorRepository;

        if (nimblegenResolver.shouldRetrieve())
            getSpreadOutInfo(apiUser, drm, sampleRecord, kickoffRequest, sample);

        if (sample.isPooledNormal()) {
            addPooledNormalDefaults(sampleRecord, apiUser, drm, kickoffRequest);
        }
    }

    public static Map<DataRecord, Collection<String>> getPooledNormals() {
        return pooledNormals.asMap();
    }

    public static List<DataRecord> getIlluminaSeqExperiments(DataRecord rec, User apiUser) {
        List<DataRecord> illuminaSeqExperiments = new ArrayList<>();

        try {
            List<DataRecord> flowCellLanes = rec.getDescendantsOfType(VeloxConstants.FLOW_CELL_LANE, apiUser);
            for (DataRecord flowCellLane : flowCellLanes) {
                List<DataRecord> flowCells = flowCellLane.getParentsOfType("FlowCell", apiUser);
                for (DataRecord flowCell : flowCells) {
                    illuminaSeqExperiments.addAll(flowCell.getParentsOfType("IlluminaSeqExperiment",
                            apiUser));
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Unable to retrieve IlluminaSeqExperiments from record " + rec.getRecordId(), e);
        }

        return illuminaSeqExperiments;
    }

    /**
     * BLAH
     **/
    void addPooledNormalDefaults(DataRecord rec, User apiUser, DataRecordManager drm, KickoffRequest kickoffRequest) {
        this.CAPTURE_BAIT_SET = ConsensusBaitSet;
        this.SPIKE_IN_GENES = ConsensusSpikeIn;
        if (!Objects.equals(this.SPIKE_IN_GENES, "na") && !this.CAPTURE_BAIT_SET.startsWith("#")) {
            this.BAIT_VERSION = this.CAPTURE_BAIT_SET + "+" + this.SPIKE_IN_GENES;
        } else {
            this.BAIT_VERSION = this.CAPTURE_BAIT_SET;
        }

        this.CMO_PATIENT_ID = optionallySetDefault(this.CMO_PATIENT_ID, this.CMO_SAMPLE_ID);
        this.INVESTIGATOR_SAMPLE_ID = optionallySetDefault(this.INVESTIGATOR_SAMPLE_ID, this.CMO_SAMPLE_ID);
        this.INVESTIGATOR_PATIENT_ID = optionallySetDefault(this.INVESTIGATOR_PATIENT_ID, this.CMO_SAMPLE_ID);
        this.ONCOTREE_CODE = optionallySetDefault(this.ONCOTREE_CODE, "Normal");
        this.SPECIMEN_COLLECTION_YEAR = optionallySetDefault(this.SPECIMEN_COLLECTION_YEAR, "000");
        this.SEX = "na";
        this.SAMPLE_CLASS = optionallySetDefault(this.SAMPLE_CLASS, "PoolNormal");
        this.SPECIES = optionallySetDefault(this.SPECIES, RequestSpecies.HUMAN.getValue());

        if (this.CMO_SAMPLE_ID.contains("MOUSE")) {
            this.SPECIES = "Mouse";
        }
        this.MANIFEST_SAMPLE_ID = this.CORRECTED_CMO_ID + "_IGO_" + this.BAIT_VERSION + "_" + this.BARCODE_INDEX;

        // According to Katenlynd the preservation type of all mouse samples will ALWAYS be Frozen. (2/20/2017)
        if (this.CMO_SAMPLE_ID.toUpperCase().contains("FROZEN") || this.SPECIES.equals("Mouse")) {
            this.SPECIMEN_PRESERVATION_TYPE = "Frozen";
        } else if (this.CMO_SAMPLE_ID.toUpperCase().contains("FFPE")) {
            this.SPECIMEN_PRESERVATION_TYPE = "FFPE";
        } else {
            this.SPECIMEN_PRESERVATION_TYPE = "Unknown";
        }

        // POOLED NORMALS don't have sample specific QC when searching the first time
        // We need to look at all the runs that were already added to the (samplesAdnRuns, or just runs)
        // Then if the pooled normal has a sample specific qc of that run type, add to include run id!
        this.INCLUDE_RUN_ID = getPooledNormalRuns(rec, apiUser, drm, kickoffRequest);
        DEV_LOGGER.info(String.format("INCLUDE RUN ID: %s", this.INCLUDE_RUN_ID));
    }

    private String getPooledNormalRuns(DataRecord rec, User apiUser, DataRecordManager drm, KickoffRequest
            kickoffRequest) {
        Set<String> allRunIds = new HashSet<>();
        Set<Run> runs = kickoffRequest.getAllValidSamples().values().stream().flatMap(s -> s.getValidRuns().stream())
                .collect(Collectors.toSet());
        for (Run run : runs) {
            allRunIds.add(run.getId());
        }

        DEV_LOGGER.info(String.format("All valid runs for request: %s", allRunIds));

        DEV_LOGGER.info(String.format("Looking at sample: %s", this.IGO_ID));

        Set<String> runIds = new TreeSet<>();

        // get decendants of sequencing qc type, check to make sure it isn't failed
        // then check to see if it is in the all Rund Ids set
        List<DataRecord> qcRecs = new ArrayList<>();
        List<Object> qcValue = new ArrayList<>();
        List<Object> qcRunID = new ArrayList<>();
        try {
            qcRecs = rec.getDescendantsOfType(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, apiUser);
            qcValue = drm.getValueList(qcRecs, VeloxConstants.SEQ_QC_STATUS, apiUser);
            qcRunID = drm.getValueList(qcRecs, VeloxConstants.SEQUENCER_RUN_FOLDER, apiUser);
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown while retrieving information about pooled normal runs", e);
        }

        if (!qcsPresent(qcRecs, qcValue, qcRunID)) {
            logWarning(String.format("No sample specific qc for ctrl %s AKA %s. Will Try to retrieve run ids from " +
                    "Flow Cell Lanes", this.CMO_SAMPLE_ID, this.IGO_ID));
            runIds = tryToFindRunByFlowCell(rec, apiUser);
        } else {
            runIds = getRunsFromQc(qcRecs, qcValue, qcRunID);
        }

        if (runIds.size() > 0) {
            Iterator<String> iterator = runIds.iterator();
            while (iterator.hasNext()) {
                String runId = iterator.next();
                if (!allRunIds.contains(runId)) {
                    iterator.remove();
                    DEV_LOGGER.debug(String.format("Omitting run %s for sample %s because this run is not part of " +
                            "request", runId, IGO_ID));
                }
            }

            return StringUtils.join(runIds, ";");
        }

        errorRepository.addWarning(new GenerationError(String.format("There is no QC for Pooled normal %s within " +
                "sample's runs: %s", IGO_ID, allRunIds), ErrorCode.POOLEDNORMAL_RUN_INVALID));

        return "";
    }

    private Set<String> getRunsFromQc(List<DataRecord> qcRecs, List<Object> qcValue, List<Object> qcRunID) {
        Set<String> runIds = new TreeSet<>();
        DEV_LOGGER.info(String.format("%d Qc records found", qcRecs.size()));

        // Go through each one, if it is 1) not "Failed" and 2) in current list of allRunIds
        for (int i = 0; i < qcRecs.size(); i++) {
            DEV_LOGGER.info(String.format("Qc record: %s", qcRecs.get(i).getRecordId()));

            String qcVal = (String) qcValue.get(i);
            if (!qcVal.startsWith("Failed")) {
                String runPath = (String) qcRunID.get(i);
                String[] pathList = runPath.split("/");
                String runName = pathList[(pathList.length - 1)];
                String pattern = "^([a-zA-Z0-9]+_[\\d]{4})([a-zA-Z\\d\\-_]+)";
                String shortRunID = runName.replaceAll(pattern, "$1");
                DEV_LOGGER.info(String.format("Short run id for pooled normal: %s", shortRunID));
                runIds.add(shortRunID);
            }
        }

        return runIds;
    }

    private boolean qcsPresent(List<DataRecord> qcRecs, List<Object> qcValue, List<Object> qcRunID) {
        return qcRecs != null && qcValue != null && qcRunID != null && qcRecs.size() > 0 && qcValue.size() > 0 &&
                qcRunID.size() > 0;
    }

    private Set<String> tryToFindRunByFlowCell(DataRecord rec, User apiUser) {
        Set<String> runIds = new HashSet<>();

        try {
            List<DataRecord> illuminaSeqExperiments = getIlluminaSeqExperiments(rec, apiUser);

            for (DataRecord illSeqExp : illuminaSeqExperiments) {
                try {
                    String runFolder = illSeqExp.getStringVal("SequencerRunFolder", apiUser);
                    String[] split = runFolder.split("/");
                    String run = split[split.length - 1];
                    Pattern p = Pattern.compile(SEQ_RUN_FOLDER_PATTERN);
                    Matcher m = p.matcher(run);
                    if (m.find()) {
                        runIds.add(m.group(1));
                    }
                } catch (Exception e) {
                    DEV_LOGGER.warn("Unable to retrieve run id from IlluminaSeqExperiment " + illSeqExp
                            .getRecordId(), e);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Unable to retrieve run id from Flow Cell Lanes", e);
        }

        return runIds;
    }

    String optionallySetDefault(String currentVal, String defaultVal) {
        if (currentVal == null || currentVal.startsWith("#") || Objects.equals(currentVal, Constants.NULL)) {
            return defaultVal;
        }
        return currentVal;
    }

    @Override
    protected void grabRequestSpecificValues(Map<String, Object> fieldMap) {    //Changes between ReqTypes
        // Sample Class changes depending on what request is being pulled.
        // HOWEVER sometimes the new value is NULL, in which case we want to keep TumorOrNormal
        this.SAMPLE_CLASS = setFromMap(this.SAMPLE_CLASS, TUMOR_OR_NORMAL, fieldMap);
        this.SAMPLE_CLASS = setFromMap(this.SAMPLE_CLASS, CMO_SAMPLE_CLASS, fieldMap);

        // These are just new values needed to track
        this.SPECIMEN_COLLECTION_YEAR = setFromMap(this.SPECIMEN_COLLECTION_YEAR, "CollectionYear", fieldMap);
        this.SEX = setFromMap(this.SEX, "Gender", fieldMap);
        this.CMO_PATIENT_ID = setFromMap(this.CMO_PATIENT_ID, "CmoPatientId", fieldMap);
        this.TISSUE_SITE = setFromMap(this.TISSUE_SITE, "TissueLocation", fieldMap);

        if (fieldMap.containsKey("DMPLibraryInput")) {
            this.LIBRARY_INPUT = setFromMap(this.LIBRARY_INPUT, "DMPLibraryInput", fieldMap);
            this.LIBRARY_YIELD = setFromMap(this.LIBRARY_YIELD, "DMPLibraryOutput", fieldMap);
        }

        // OnocTree Code
        this.ONCOTREE_CODE = optionallySetDefault(this.ONCOTREE_CODE, "#UNKNOWN");
        String tumorLong = setFromMap(this.ONCOTREE_CODE, Constants.ProjectInfo.TUMOR_TYPE, fieldMap);
        if (!Objects.equals(tumorLong, Constants.NULL) && tumorLong.length() > 0) {
            String[] tumor_type = tumorLong.split("[()]");
            if (tumor_type.length == 2) {
                this.ONCOTREE_CODE = tumor_type[1];
            } else if (tumor_type.length == 1 && tumor_type[0].length() > 1) {
                this.ONCOTREE_CODE = tumor_type[0];
            }
            if (this.SAMPLE_CLASS.contains("Normal")) {
                // If sample class is normal override tumor type and set to "na"
                this.ONCOTREE_CODE = "na";
            }
        } else if (this.SAMPLE_CLASS.equals("Normal")) {
            this.ONCOTREE_CODE = "na";
        } else {
            if (this.ONCOTREE_CODE.startsWith("#")) {
                this.ONCOTREE_CODE = "#UNKNOWN";
            }
        }
    }

    @Override
    protected void populateDefaultFields() {
        fieldDefaults.put(VeloxConstants.SAMPLE_ID, Constants.EMPTY);
        fieldDefaults.put("OtherSampleId", Constants.EMPTY);
        fieldDefaults.put("PatientId", Constants.EMPTY);
        fieldDefaults.put("UserSampleID", Constants.EMPTY);
        fieldDefaults.put("SpecimenType", "na");
        fieldDefaults.put("Preservation", Constants.EMPTY);
        fieldDefaults.put("Species", "#UNKNOWN");
        fieldDefaults.put("CorrectedCMOID", Constants.EMPTY);
        fieldDefaults.put("RequestId", Constants.EMPTY);
        fieldDefaults.put(TUMOR_OR_NORMAL, Constants.EMPTY);
        fieldDefaults.put("CollectionYear", "000");

        fieldDefaults.put("CmoPatientId", Constants.EMPTY);
        fieldDefaults.put("Gender", "Unknown");
        fieldDefaults.put("TissueLocation", "na");
    }

    void getSpreadOutInfo(User apiUser, DataRecordManager drm, DataRecord rec, KickoffRequest kickoffRequest, Sample
            sample) {
        // Spread out information available for IMPACT includes this.BARCODE_ID, this.BARCODE_INDEX
        // this.LIBRARY_YIELD this.LIBRARY_INPUT
        // this.CAPTURE_NAME this.CAPTURE_CONCENTRATION
        // this.CAPTURE_INPUT
        // TODO: Find IGO_ID of pool sample that is between

        // This only runs if LIBRARY_INPUT was not given already in CMO Sample Info Data Record
        if (this.LIBRARY_INPUT == null || this.LIBRARY_INPUT.startsWith("#")) {
            this.LIBRARY_INPUT = Constants.EMPTY;
            grabLibInput(drm, rec, apiUser, false, sample.isPooledNormal());
            if (sample.isTransfer() && this.LIBRARY_INPUT.startsWith("#")) {
                grabLibInputFromPrevSamps(drm, rec, apiUser, sample.isPooledNormal());
            }
            if (this.LIBRARY_INPUT.startsWith("#")) {
                logWarning(String.format("Unable to find DNA Lib Protocol for Library Input method (sample %s)", this
                        .CMO_SAMPLE_ID));
                this.LIBRARY_INPUT = "-2";
            }
        }

        getBarcodeInfo(drm, apiUser, kickoffRequest, sample);

        // Capture concentration
        grabCaptureConc(rec, apiUser);


        if (this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#")) {
            this.LIBRARY_YIELD = Constants.EMPTY;
        }

        // DEBUGGING. Print tree. I will recursively go through a sample and print their children data types and then
        // grab the child samples and do it again.
        //printTree(rec, apiUser, 0);

        // If Nimblgen Hybridization was created BEFORE May 5th, ASSUME
        // That Library Yield, and Capture INPUT is correct on Nimblgen data record
        // After that date we MUST get the information from elsewhere
        boolean afterDate = nimbAfterMay5(drm, rec, apiUser, VeloxConstants.NIMBLE_GEN_HYB_PROTOCOL);
        if (afterDate) {
            // First try and get elution volume from Protocol3
            // Otherwise try to get from Protocol 2
            // THEN try to get from 3 using prev samples
            // THEN THEN try to get from 2 using prev samples
            double libVol = getLibraryVolume(rec, apiUser, false, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_3);
            if (libVol <= 0) {
                libVol = getLibraryVolume(rec, apiUser, false, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_2);
            }
            if (libVol <= 0 && sample.isTransfer()) {
                libVol = getLibraryVolumeFromPrevSamps(rec, apiUser, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_3);
                if (libVol <= 0) {
                    libVol = getLibraryVolumeFromPrevSamps(rec, apiUser, VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_2);
                }
            }
            double libConc = getLibraryConcentration(rec, drm, apiUser, sample.isTransfer(), VeloxConstants
                    .NIMBLE_GEN_HYB_PROTOCOL);
            if (libVol > 0 && libConc > 0 && this.LIBRARY_YIELD.startsWith("#")) {
                this.LIBRARY_YIELD = String.valueOf(df.format(libVol * libConc));
            } else if (this.LIBRARY_YIELD.startsWith("#")) {
                this.LIBRARY_YIELD = "-2";
            }
            processNimbInfo(drm, rec, apiUser, libVol, libConc, sample.isPooledNormal(), afterDate, sample
            );
        } else {
            // Here I can just be simple like the good old times and pull stuff from the
            // Nimb protocol
            processNimbInfo(drm, rec, apiUser, -1, -1, sample.isPooledNormal(), afterDate, sample);
        }
    }

    boolean nimbAfterMay5(DataRecordManager drm, DataRecord rec, User apiUser, String protocolName) {
        // Here, I am just going to get creation date of this sample. If it is after
        // May 5th, 2016, I return yes. Otherwise return no.
        // This is the only way I can get as much correct information as possible.

        Date cutoffDate = new Date();
        List<DataRecord> nimbProtocols = new ArrayList<>();
        List<Object> validity = new ArrayList<>();
        List<Object> creationDate = new ArrayList<>();
        Set<Date> createDates = new HashSet<>();

        // Grabbing the items I need to see if this is an OK nimb to use
        try {
            cutoffDate = sdf.parse("2016-05-05");
            nimbProtocols = rec.getDescendantsOfType(protocolName, apiUser);
            validity = drm.getValueList(nimbProtocols, "Valid", apiUser);
            creationDate = drm.getValueList(nimbProtocols, "DateCreated", apiUser);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information for protocol: %s",
                    protocolName), e);
        }

        if (nimbProtocols == null || validity == null || creationDate == null) {
            logError(String.format("Unknown error in checked creation date of %s protocols", protocolName));
            return false;
        }
        if (nimbProtocols.size() == 0 || validity.size() == 0 || creationDate.size() == 0) {
            logWarning(String.format("Not able to find %s Protocol or date created", protocolName));
            return false;
        }

        //Iterate, check validity, add createDate to a HashSet of Date
        for (int i = 0; i < nimbProtocols.size(); i++) {
            boolean valid;
            try {
                valid = (boolean) validity.get(i);
            } catch (NullPointerException e) {
                valid = false;
            }
            // I don't care if it is false or null. Only if true.
            if (valid) {
                Date thisDate = new Date((long) creationDate.get(i));
                createDates.add(thisDate);
            }
        }

        // Check all the dates, if ANY are after may5th return true
        for (Date d : createDates) {
            if (d.compareTo(cutoffDate) > 0) {
                return true;
            }
        }
        // This means none were after May 5th
        return false;
    }

    void getBarcodeInfo(DataRecordManager drm, User apiUser, KickoffRequest kickoffRequest, Sample sample) {
        // This will give us this.BARCODE_ID and this.BARCODE_INDEX
        //Find the Sample Specific QC record for this sample, and go up until you find an index.
        List<DataRecord> qcRecs = new ArrayList<>();
        try {
            // First find sample specific qc with this cmo sample id, one of the run IDs,
            qcRecs = drm.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, "Request = '" + this.REQUEST_ID + "'" +
                    " AND OtherSampleId = '" + this.CMO_SAMPLE_ID + "'", apiUser);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Sequence analysis " +
                    "sample QC"), e);
        }
        // Now I can iterate, the qcRecs, and I have to check to see if the runIDs are the same as above
        if (qcRecs == null || qcRecs.size() == 0) {
            if (sample.isPooledNormal() || kickoffRequest.isForced()) {
                String extraInput = "";
                if (!sample.isPooledNormal()) {
                    extraInput = "_";
                }
                try {
                    List<DataRecord> indexBarcodes = drm.queryDataRecords("IndexBarcode", "SampleId LIKE '" + this
                            .IGO_ID + extraInput + "%'", apiUser);
                    if (indexBarcodes != null && indexBarcodes.size() > 0) {
                        DataRecord bc = indexBarcodes.get(indexBarcodes.size() - 1);
                        this.BARCODE_ID = bc.getStringVal("IndexId", apiUser);
                        this.BARCODE_INDEX = bc.getStringVal("IndexTag", apiUser);
                        return;
                    }
                } catch (Exception e) {
                    DEV_LOGGER.error(String.format("Exception thrown while retrieving information about Index Barcode" +
                            " for sample: %s", this.IGO_ID), e);
                }
            }
            return;
        }

        DataRecord qcToUse = null;
        String[] runParts;
        for (DataRecord qcRecord : qcRecs) {
            try {
                runParts = qcRecord.getStringVal("SequencerRunFolder", apiUser).split("_");
                if (runParts.length < 2) {
                    logError("sequencingRunFolder incorrectly split by '_', or not correctly pulled.");
                }

                String RunID = runParts[0] + "_" + runParts[1];
                if (!kickoffRequest.getSamples().containsKey(this.IGO_ID) && !kickoffRequest.getSamplesForProjects()
                        .containsKey(IGO_ID)) {
                    logError("The sample ID I am using to search for runs is not the correct sample ID.");
                    return;
                }
                //@TODO keep sample as class's field to use throughout this class
                if ((kickoffRequest.getSamples().containsKey(this.IGO_ID) && kickoffRequest.getSample(this.IGO_ID)
                        .containsRun(RunID)) || kickoffRequest.getSamplesForProjects().get(IGO_ID).containsRun(RunID)) {
                    // When I find a qc that matches the run ID, I can exit this for loop. I only need one because
                    // they should all have (the same) barcode upstream
                    qcToUse = qcRecord;
                    break;
                }
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Sequencer run " +
                        "folder"), e);
            }
        }

        // Now I want to get all the above things that have DR type sample, and look to see if they have children of
        // type Index.
        if (qcToUse != null) {
            try {
                List<DataRecord> sampAncestors = qcToUse.getAncestorsOfType("Sample", apiUser);
                List<List<Map<String, Object>>> indexRecsPerSamp = drm.getFieldsForChildrenOfType(sampAncestors,
                        "IndexBarcode", apiUser);
                if (indexRecsPerSamp != null || indexRecsPerSamp.get(0) != null || indexRecsPerSamp.size() != 0) {
                    for (int x = 0; x < indexRecsPerSamp.size(); x++) {
                        //This Iterates index of the sampancestors results, now you go through each children of type
                        // Index barcode child.
                        List<Map<String, Object>> barcodeRecs = indexRecsPerSamp.get(x);
                        if (barcodeRecs.size() == 0) {
                            continue;
                        }
                        // If there are more than one records, I only need the first one
                        // IF there is more than one and they are different, I wouldn't be
                        // able to tell which is the correct one to use anyway.
                        Map<String, Object> fieldMap = barcodeRecs.get(0);
                        this.BARCODE_ID = setFromMap(this.BARCODE_ID, "IndexId", fieldMap);
                        this.BARCODE_INDEX = setFromMap(this.BARCODE_INDEX, "IndexTag", fieldMap);

                        // Now get the sample parent's IGO ID for sequencing
                        // TODO: verify that this is correct the correct place to get it from
                        DataRecord samp = sampAncestors.get(x);
                        this.SEQ_IGO_ID = samp.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);

                        break;
                    }
                }
            } catch (Exception e) {
                DEV_LOGGER.error(String.format("Exception thrown while retrieving information about sample " +
                        "ancestors"), e);
            }
        }
    }

    /*
     Library Input
     */
    void grabLibInputFromPrevSamps(DataRecordManager drm, DataRecord rec, User apiUser, Boolean poolNormal) {
        // recursive method that goes up the sample records, looking for ones that have children
        // DNALibraryPrepProtocol1, or KapaAutoNormalizationProtocol
        List<DataRecord> psamp = new ArrayList<>();
        try {
            psamp = rec.getParentsOfType("Sample", apiUser);
        } catch (Exception e) {
            DEV_LOGGER.error(String.format("Exception thrown while retrieving information about sample parents"), e);
        }
        for (DataRecord samp : psamp) {
            String dnaLibProtocol = VeloxConstants.KAPA_LIB_PLATE_SETUP_PROTOCOL_1;
            String kapaAutoNormalProtocol = VeloxConstants.KAPA_AUTO_NORMALIZATION_PROTOCOL;

            try {
                List<DataRecord> DNALibPrep = Arrays.asList(samp.getChildrenOfType(dnaLibProtocol, apiUser));
                List<DataRecord> KapaAutoNormProt = Arrays.asList(samp.getChildrenOfType(kapaAutoNormalProtocol,
                        apiUser));
                if (DNALibPrep.size() > 0 || KapaAutoNormProt.size() > 0) {
                    grabLibInput(drm, samp, apiUser, true, poolNormal);
                }
            } catch (Exception e) {
                DEV_LOGGER.error(String.format("Exception thrown while retrieving information about sample protocols:" +
                        " %s and %s", dnaLibProtocol, kapaAutoNormalProtocol), e);
            }
            if (!this.LIBRARY_INPUT.startsWith("#")) {
                return;
            }
            grabLibInputFromPrevSamps(drm, samp, apiUser, poolNormal);
        }

    }

    void grabLibInput(DataRecordManager drm, DataRecord rec, User apiUser, Boolean childrenOnly, Boolean poolNormal) {
        String targetMassAliquote = VeloxConstants.TARGET_MASS_ALIQ_1;
        if (poolNormal) {
            this.LIBRARY_INPUT = "0";
            List<DataRecord> DNALibPreps = null;
            String dnaLibPrepProtocol = VeloxConstants.DNA_LIBRARY_PREP_PROTOCOL_1;
            try {
                DNALibPreps = drm.queryDataRecords(dnaLibPrepProtocol, "SampleId = '" + this.IGO_ID + "'", apiUser);
            } catch (Exception e) {
                DEV_LOGGER.error(String.format("Exception thrown while retrieving information about protocol: %s for " +
                        "sample: %s", dnaLibPrepProtocol, this.IGO_ID), e);
            }
            if (DNALibPreps != null && DNALibPreps.size() > 0) {
                DataRecord DNALP = DNALibPreps.get(DNALibPreps.size() - 1);
                try {
                    this.LIBRARY_INPUT = String.valueOf(df.format(DNALP.getDoubleVal(targetMassAliquote, apiUser)));
                } catch (Exception e) {
                    DEV_LOGGER.error(String.format("Exception thrown while retrieving information about Target mass " +
                            "Aliquote: %s", targetMassAliquote), e);
                }
            }
            return;
        }
        Boolean Normalization_DR = false;
        List<DataRecord> KANP = new ArrayList<>();
        String kapaLibPlateSetupProtocol = VeloxConstants.KAPA_LIB_PLATE_SETUP_PROTOCOL_1;
        String kapaAutoNormalizationProtocol = VeloxConstants.KAPA_AUTO_NORMALIZATION_PROTOCOL;
        try {
            // First check KAPALibPlateSetupProtocol1 (newer data record..)
            // If that is empty try to find the old one
            if (childrenOnly) {
                KANP = Arrays.asList(rec.getChildrenOfType(kapaLibPlateSetupProtocol, apiUser));
                if (KANP.size() == 0) {
                    KANP = Arrays.asList(rec.getChildrenOfType(kapaAutoNormalizationProtocol, apiUser));
                    if (KANP.size() == 0) {
                        return;
                    }
                    Normalization_DR = true;
                }
            } else {
                KANP = rec.getDescendantsOfType(kapaLibPlateSetupProtocol, apiUser);
                if (KANP == null || KANP.size() == 0) {
                    KANP = rec.getDescendantsOfType(kapaAutoNormalizationProtocol, apiUser);
                    if (KANP == null || KANP.size() == 0) {
                        return;
                    }
                    Normalization_DR = true;
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error(String.format("Exception thrown while retrieving information about protocols: %s and " +
                    "%s", kapaLibPlateSetupProtocol, kapaAutoNormalizationProtocol), e);
        }
        if (KANP != null && KANP.size() > 0) {
            int largestValidIndex = -1;
            if (!Normalization_DR) {
                // for each item in KANP, check valid box.
                // if not true, add index to list to remove from list.
                List<Object> validity = new ArrayList<>();
                try {
                    validity = drm.getValueList(KANP, "Preservation", apiUser);
                } catch (Exception e) {
                    DEV_LOGGER.error(String.format("Exception thrown while retrieving information about " +
                            "Preservation"), e);
                }

                if (validity != null && validity.size() > 0) {
                    for (int x = 0; x < validity.size(); x++) {
                        try {
                            if (!((String) validity.get(x)).isEmpty() && validity.get(x) != null) {
                                largestValidIndex = x;
                            }
                        } catch (NullPointerException ignored) {
                        }
                    }
                }

                // if none are valid, make this.LIBRARY_INPUT == #empty
                if (largestValidIndex < 0) {
                    this.LIBRARY_INPUT = Constants.EMPTY;
                    return;
                }
            } else {
                largestValidIndex = KANP.size() - 1;
            }

            // As per Agnes who talked to Mike Berger, If there are multiple Kapa protocols,
            // Simply pick the LAST one that is in the list. list.size() - 1
            String val = "";
            try {
                if (Normalization_DR) {
                    val = String.valueOf(df.format(KANP.get(largestValidIndex).getDoubleVal("Aliq2TargetMass",
                            apiUser)));
                } else {
                    val = String.valueOf(df.format(KANP.get(largestValidIndex).getDoubleVal(targetMassAliquote,
                            apiUser)));
                }
            } catch (Exception e) {

            }
            if (val != null && Double.parseDouble(val) > 0) {
                this.LIBRARY_INPUT = val;
            } else {
                this.LIBRARY_INPUT = Constants.EMPTY;
            }
        }
    }

    /*
     Library Volume
     */
    double getLibraryVolume(DataRecord rec, User apiUser, Boolean childOnly, String dataRecordName) {
        // Th/is gets the elution volume
        double input = -1;
        List<DataRecord> DNALibPreps = new ArrayList<>();
        try {
            if (childOnly) {
                DNALibPreps = Arrays.asList(rec.getChildrenOfType(dataRecordName, apiUser));
            } else {
                DNALibPreps = rec.getDescendantsOfType(dataRecordName, apiUser);
            }
        } catch (Exception e) {

        }
        if (DNALibPreps == null || DNALibPreps.size() == 0) {
            return -9;
        }
        for (DataRecord n1 : DNALibPreps) {
            // Must check for "Valid" checkbox because some are not valid.
            Boolean real;
            try {
                real = n1.getBooleanVal("Valid", apiUser);
            } catch (Exception e) {
                real = false;
            }
            if (real) {
                try {
                    input = n1.getDoubleVal(VeloxConstants.ELUTION_VOL, apiUser);
                } catch (NullPointerException e) {
                    input = -1;
                    logError(String.format("Cannot find elution vol for %s AKA %s Using DataRecord %s", this
                            .CMO_SAMPLE_ID, this.IGO_ID, dataRecordName));
                } catch (Exception e) {
                    DEV_LOGGER.warn("Exception thrown while retrieving information about Elution Volume", e);
                }
            }
        }
        return input;
    }

    double getLibraryVolumeFromPrevSamps(DataRecord rec, User apiUser, String dataRecordName) {
        double result = -1;
        List<DataRecord> psamp = new ArrayList<>();

        try {
            psamp = rec.getParentsOfType("Sample", apiUser);
        } catch (Exception e) {
            DEV_LOGGER.error(e);

        }

        for (DataRecord samp : psamp) {
            result = getLibraryVolume(samp, apiUser, true, dataRecordName);
            if (result > 0) {
                return result;
            }
            result = getLibraryVolumeFromPrevSamps(samp, apiUser, dataRecordName);
        }
        return result;
    }

    /*
     Library Concentration
     */

    double getLibraryConcentration(DataRecord rec, DataRecordManager drm, User apiUser, Boolean transfer, String
            alternativeDR) {
        // If there is a DNALibParent present (VIA get LibVolume)
        DataRecord libConcParent = null;

        double conc_ng;

        // Don't use DNA Library Prep Protocl anymore. Find alternativeDR, and use that to find the parent
        // sample type

        List<DataRecord> grandparents = new ArrayList<>();
        try {
            List<DataRecord> altDrs = rec.getDescendantsOfType(alternativeDR, apiUser);
            if (altDrs != null && altDrs.size() > 0) {
                List<Object> validity = drm.getValueList(altDrs, "Valid", apiUser);
                List<Object> igoId = drm.getValueList(altDrs, VeloxConstants.SAMPLE_ID, apiUser);
                if (validity != null && validity.size() > 0) {
                    for (int x = 0; x < validity.size(); x++) {
                        try {
                            if (((boolean) validity.get(x)) && ((String) igoId.get(x)).contains(this.IGO_ID)) {
                                DataRecord chosenAltDr = altDrs.get(x);
                                List<DataRecord> parents = chosenAltDr.getParentsOfType("Sample", apiUser);
                                if (parents != null && parents.size() > 0) {
                                    libConcParent = parents.get(0);
                                    if (transfer) {
                                        grandparents = libConcParent.getParentsOfType("Sample", apiUser);
                                    }
                                }
                            }
                        } catch (NullPointerException ignored) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e);

        }

        if (libConcParent == null) {
            logWarning(String.format("Unable to find lib concentration for %s", this.IGO_ID));
            return -9;
        }

        // Pull concentration values in libConcParent if this is okay.
        conc_ng = pullConcValues(drm, apiUser, libConcParent);

        // IF conc_ng is less than 0, check to see if the grandparent datarecord has a different
        // request. ONLY THEN redo pullConcValues, but with grandparent sample
        if (conc_ng <= 0 && grandparents != null && grandparents.size() > 0) {
            DataRecord gp = grandparents.get(0);
            String request = this.REQUEST_ID;
            try {
                request = gp.getStringVal("RequestId", apiUser);
            } catch (Exception e) {
                DEV_LOGGER.warn("Exception thrown while getting Request id", e);
            }

            if (!request.equals(this.REQUEST_ID)) {
                conc_ng = pullConcValues(drm, apiUser, gp);
            }
        }

        return conc_ng;

    }

    private double pullConcValues(DataRecordManager drm, User apiUser, DataRecord libConcParent) {
        String concentration;
        double conc_ng = -1;
        List<DataRecord> DLP = new ArrayList<>();
        DLP.add(libConcParent);
        List<Map<String, Object>> concRecs = new ArrayList<>();


        // First try to look up MolarConcentrationAssignment
        // If not present look up QCDatum ** more rules with QCDatum below
        try {
            concRecs = drm.getFieldsForChildrenOfType(DLP, "MolarConcentrationAssignment", apiUser).get(0);
        } catch (Exception e) {
            DEV_LOGGER.error(e);

        }
        // If there are Molar Concentratin Assignments
        if (concRecs != null || concRecs.size() > 0) {
            for (Map<String, Object> ma : concRecs) {
                // Only look at concentrations that are ng/ul
                String concUnits = (String) ma.get("ConcentrationUnits");
                if (!(concUnits.toLowerCase()).equals("ng/ul")) {
                    continue;
                }
                concentration = setFromMap("-1", "Concentration", ma);
                if (concentration != null && Double.parseDouble(concentration) > 0) {
                    conc_ng = Double.parseDouble(concentration);
                    // only need the first available concentration (as of now).
                    return conc_ng;
                }
            }
        }
        // If this doesn't work, try using QC Datum
        if (conc_ng <= 0) {
            try {
                concRecs = drm.getFieldsForChildrenOfType(DLP, "QCDatum", apiUser).get(0);
            } catch (Exception e) {
                DEV_LOGGER.error(e);

            }
            for (Map<String, Object> n2 : concRecs) {
                String type = (String) n2.get("DatumType");
                //DO NOT grab from tapestation or bioanalyzer. They are not accurate (as per Kate).
                if (!type.startsWith("TapeStation") && !type.startsWith("Bioanalyzer")) {
                    if (!type.startsWith("Qubit")) {
                        logInfo(String.format("This qc datum is type %s, I will try to pull concentration from it.",
                                type));
                    }
                    concentration = setFromMap("-1", "CalculatedConcentration", n2);
                    if (concentration != null && Double.parseDouble(concentration) > 0) {
                        conc_ng = Double.parseDouble(concentration);
                        // only need the first available concentration (as of now).
                        return conc_ng;
                    }
                }
            }
        }
        return conc_ng;
    }

    private void processNimbInfo(DataRecordManager drm, DataRecord rec, User apiUser, double libVol, double libConc,
                                 boolean isPoolNormal, boolean afterDate, Sample sample) {
        try {
            DataRecord chosenRec = nimblegenResolver.resolve(drm, rec, apiUser, isPoolNormal);
            sample.setHasNimbleGen(true);
            Map<String, Object> nimbRec = new HashMap<>();

            try {
                nimbRec = chosenRec.getFields(apiUser);
            } catch (Exception e) {
                DEV_LOGGER.error("Exception thrown while getting fields for record", e);

            }

            if (nimbRec == null || nimbRec.size() == 0) {
                logError("Unknown error while pulling getFields");
                return;
            }

            // If libVol & libConc not null or negative make LibYield
            if (libVol > 0 && libConc > 0 && this.LIBRARY_YIELD.startsWith("#")) {
                this.LIBRARY_YIELD = String.valueOf(df.format(libVol * libConc));
            }

            //Deal with if lib yield is not filled out
            if (this.LIBRARY_YIELD.startsWith("#") && !afterDate) {
                if (libVol <= 0) {
                    // now I have to get the vol from the data record
                    double vol = Double.parseDouble(setFromMap("-1", "StartingVolume", nimbRec));
                    double conc = Double.parseDouble(setFromMap("-1", "StartingConcentration", nimbRec));
                    if (vol > 0 && conc > 0) {
                        this.LIBRARY_YIELD = String.valueOf(df.format(vol * conc));
                    }
                } else {
                    //Only need concentration
                    double conc = Double.parseDouble(setFromMap("-1", "StartingConcentration", nimbRec));
                    if (conc > 0) {
                        this.LIBRARY_YIELD = String.valueOf(df.format(libVol * conc));
                    }
                }
                // for ease, just grab the other thing besides voltoUse
                this.CAPTURE_INPUT = setFromMap(Constants.EMPTY, "SourceMassToUse", nimbRec);
            }
            if (libConc > 0) {
                double volumetoUse = Double.parseDouble(setFromMap("-1", "VolumeToUse", nimbRec));
                if (volumetoUse > 0) {
                    this.CAPTURE_INPUT = String.valueOf(Math.round(libConc * volumetoUse));
                }
            } else {
                this.CAPTURE_INPUT = "-2";
            }
            // Now the rest of the stuff
            this.CAPTURE_NAME = setFromMap(Constants.EMPTY, "Protocol2Sample", nimbRec);
            this.CAPTURE_BAIT_SET = setFromMap(Constants.EMPTY, VeloxConstants.RECIPE, nimbRec);
            this.SPIKE_IN_GENES = setFromMap("na", VeloxConstants.SPIKE_IN_GENES, nimbRec);

            if (!Objects.equals(this.SPIKE_IN_GENES, "na") && !this.CAPTURE_BAIT_SET.startsWith("#")) {
                this.BAIT_VERSION = this.CAPTURE_BAIT_SET + "+" + this.SPIKE_IN_GENES;
            } else {
                this.BAIT_VERSION = this.CAPTURE_BAIT_SET;
            }

            if (!isPoolNormal) {
                ConsensusBaitSet = this.CAPTURE_BAIT_SET;
                ConsensusSpikeIn = this.SPIKE_IN_GENES;
            }
        } catch (RecordNimblegenResolver.NoNimblegenHybProtocolFound e) {
            logError("No NoNymbHybProtocol DataRecord found for " + SampleInfoImpact.this.CMO_SAMPLE_ID + "(" +
                    SampleInfoImpact.this.IGO_ID + "). The " +
                    "baitset/spikin, Capture Name, Capture Input, Library Yield will be unavailable. ");
        } catch (RecordNimblegenResolver.NoValidNimblegenHybrFound e) {
            logError(String.format("No VALID NimblgenHybridizationProtocol DataRecord found for %s(%s). %s The " +
                            "baitset/spikin, Capture Name, Capture Input, Library Yield will be unavailable. ",
                    SampleInfoImpact.this
                            .CMO_SAMPLE_ID, SampleInfoImpact.this.IGO_ID, poolNameList));
        }
    }

    /*
     Capture Concentration
     */
    void grabCaptureConc(DataRecord rec, User apiUser) {
        // Since soon we won't have any way to find out if this was from a passing
        // pool that was sequenced, I'm just going to assume it is.
        List<DataRecord> captureRecords;
        try {
            captureRecords = rec.getDescendantsOfType("NormalizationPooledLibProtocol", apiUser);
            if (captureRecords != null && captureRecords.size() != 0) {
                for (DataRecord CaptureInfo : captureRecords) {
                    double captConc;
                    try {
                        captConc = CaptureInfo.getDoubleVal("InputLibMolarity", apiUser);
                    } catch (NullPointerException e) {
                        captConc = 0;
                    }
                    if (captConc > 0) {
                        this.CAPTURE_CONCENTRATION = String.valueOf(captConc);
                    } else {
                        this.CAPTURE_CONCENTRATION = Constants.EMPTY;
                    }

                    String name = CaptureInfo.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);
                    if (name != null && name.length() > 0) {
                        this.CAPTURE_NAME = name;
                    }
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error("Exception thrown while retrieving information about capture concentration", e);
        }
    }

    @Override
    public LinkedHashMap<String, String> sendInfoToMap() {
        LinkedHashMap<String, String> myMap = new LinkedHashMap<>();
        myMap.put(Constants.IGO_ID, this.IGO_ID);
        myMap.put("EXCLUDE_RUN_ID", this.EXCLUDE_RUN_ID);
        myMap.put(Constants.INCLUDE_RUN_ID, this.INCLUDE_RUN_ID);
        myMap.put("INVESTIGATOR_PATIENT_ID", this.INVESTIGATOR_PATIENT_ID);
        myMap.put("INVESTIGATOR_SAMPLE_ID", this.INVESTIGATOR_SAMPLE_ID);
        myMap.put("SAMPLE_CLASS", this.SAMPLE_CLASS);
        myMap.put("SAMPLE_TYPE", this.SAMPLE_TYPE);
        myMap.put("SPECIMEN_PRESERVATION_TYPE", this.SPECIMEN_PRESERVATION_TYPE);
        myMap.put("SPECIES", this.SPECIES);
        myMap.put("STATUS", this.STATUS);
        myMap.put("MANIFEST_SAMPLE_ID", this.MANIFEST_SAMPLE_ID);
        myMap.put("CORRECTED_CMO_ID", this.CORRECTED_CMO_ID);
        myMap.put("SEQ_IGO_ID", this.SEQ_IGO_ID);
        myMap.put(Constants.BARCODE_ID, this.BARCODE_ID);
        myMap.put(Constants.BARCODE_INDEX, this.BARCODE_INDEX);
        myMap.put(Constants.CMO_SAMPLE_ID, this.CMO_SAMPLE_ID);
        myMap.put("LIBRARY_INPUT", this.LIBRARY_INPUT);
        myMap.put("SPECIMEN_COLLECTION_YEAR", this.SPECIMEN_COLLECTION_YEAR);
        myMap.put("ONCOTREE_CODE", this.ONCOTREE_CODE);
        myMap.put("TISSUE_SITE", this.TISSUE_SITE);
        myMap.put("SEX", this.SEX);
        myMap.put("LIBRARY_YIELD", this.LIBRARY_YIELD);
        myMap.put("CMO_PATIENT_ID", this.CMO_PATIENT_ID);
        myMap.put("CAPTURE_NAME", this.CAPTURE_NAME);
        myMap.put(Constants.CAPTURE_INPUT, this.CAPTURE_INPUT);
        myMap.put("CAPTURE_CONCENTRATION", this.CAPTURE_CONCENTRATION);
        myMap.put(Constants.BAIT_VERSION, this.BAIT_VERSION);
        myMap.put("CAPTURE_BAIT_SET", this.CAPTURE_BAIT_SET);
        myMap.put("SPIKE_IN_GENES", this.SPIKE_IN_GENES);

        return myMap;
    }

    public void logInfo(String message) {
        PM_LOGGER.info(message);
        DEV_LOGGER.info(message);
    }
}
