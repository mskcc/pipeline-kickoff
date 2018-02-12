package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.*;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.lims.SampleInfo;
import org.mskcc.kickoff.lims.SampleInfoExome;
import org.mskcc.kickoff.lims.SampleInfoImpact;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.velox.util.VeloxConstants;
import org.mskcc.kickoff.velox.util.VeloxUtils;
import org.springframework.beans.factory.annotation.Value;

import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.forced;
import static org.mskcc.kickoff.config.Arguments.runAsExome;
import static org.mskcc.kickoff.util.Utils.getJoinedCollection;

public class VeloxRequestProxy implements RequestProxy {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final Map<Sample, DataRecord> sampleToDataRecord = new HashMap<>();

    private DataRecordManager dataRecordManager;
    private User user;
    private List<Request> requests = new ArrayList<>();
    private VeloxConnection connection;

    @Value("${limsConnectionFilePath}")
    private String limsConnectionFilePath;
    private List<DataRecord> originalSampRec;
    private ProjectFilesArchiver projectFilesArchiver;

    public VeloxRequestProxy(ProjectFilesArchiver projectFilesArchiver) {
        this.projectFilesArchiver = projectFilesArchiver;
    }

    @Override
    public List<Request> getRequests(String requestId) {
        initVeloxConnection();

        try {
            VeloxStandalone.run(connection, new VeloxTask<Object>() {
                @Override
                public Object performTask() throws VeloxStandaloneException {
                    retrieveRequest(requestId);
                    return new Object();
                }
            });
        } catch (VeloxStandaloneException e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
        return requests;
    }

    private void initVeloxConnection() {
        try {
            connection = VeloxUtils.getVeloxConnection(limsConnectionFilePath);
            connection.open();

            if (connection.isConnected()) {
                user = connection.getUser();
                dataRecordManager = connection.getDataRecordManager();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void closeConnection() {
        if (connection.isConnected()) {
            try {
                connection.close();
            } catch (Throwable e) {
                DEV_LOGGER.error(e.getMessage(), e);
            }
        }
    }

    private void retrieveRequest(String requestId) {
        try {
            List<DataRecord> requestsDataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" + requestId + "'", user);

            for (DataRecord dataRecordRequest : requestsDataRecords) {
                originalSampRec = getOriginalSampleRecords(dataRecordRequest);
                Request request = new Request(requestId, new NormalProcessingType(projectFilesArchiver));
                setReadMe(request, dataRecordRequest);
                setName(request, dataRecordRequest);
                setRecipe(request, dataRecordRequest);
                setProperties(request, dataRecordRequest);

                validateSequencingRuns(request);
                getLibTypes(request, dataRecordRequest);
                setReqType(request);

                //@TODO cehck if I cant pool after process samples
                addPoolSeqQc(request, dataRecordRequest, originalSampRec);
                processPlates(request, dataRecordRequest);
                processSamples(request, dataRecordRequest);
                addPoolRunsToSamples(request);

                addSampleInfo(request);

                processPooledNormals(request, dataRecordRequest);

                setPairings(request, dataRecordRequest);
                requests.add(request);
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        } finally {
            closeConnection();
        }
    }

    private List<DataRecord> getOriginalSampleRecords(DataRecord dataRecordRequest) throws RemoteException, IoError {
        ArrayList<DataRecord> originalSampRec = new ArrayList<>();
        ArrayList<DataRecord> samples = new ArrayList<>(dataRecordRequest.getDescendantsOfType(VeloxConstants.SAMPLE, user));
        for (DataRecord sample : samples) {
            ArrayList<DataRecord> requests = new ArrayList<>(sample.getParentsOfType(VeloxConstants.REQUEST, user));
            ArrayList<DataRecord> plates = new ArrayList<>(sample.getParentsOfType(VeloxConstants.PLATE, user));
            if (!requests.isEmpty()) {
                originalSampRec.add(sample);
            } else if (!plates.isEmpty()) {
                for (DataRecord p : plates) {
                    if (p.getParentsOfType(VeloxConstants.REQUEST, user).size() != 0) {
                        originalSampRec.add(sample);
                        break;
                    }
                }
            }
        }
        return originalSampRec;
    }

    private void setReadMe(Request request, DataRecord dataRecordRequest) {
        String readMe = "";
        try {
            readMe = dataRecordRequest.getStringVal(Constants.READ_ME, user);
        } catch (Exception e) {

        }
        request.setReadMe(readMe);
    }

    private void setName(Request request, DataRecord dataRecordRequest) {
        String requestName = "";
        try {
            requestName = dataRecordRequest.getPickListVal(Constants.REQUEST_NAME, user);
        } catch (Exception e) {
        }
        request.setName(requestName);
    }

    private void setRecipe(Request request, DataRecord dataRecordRequest) {
        //this will get the recipes for all sampels under request
        List<Object> recipes = new ArrayList<>();

        try {
            List<DataRecord> samples = Arrays.asList(dataRecordRequest.getChildrenOfType(VeloxConstants.SAMPLE, user));
            recipes = dataRecordManager.getValueList(samples, VeloxConstants.RECIPE, user);
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }

        try {
            List<Recipe> recipeList = recipes.stream()
                    .map(r -> Recipe.getRecipeByValue(r.toString()))
                    .distinct()
                    .collect(Collectors.toList());
            request.setRecipe(recipeList);
        } catch (Recipe.UnsupportedRecipeException e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }
    }

    private void addPoolRunsToSamples(Request request) {
        Set<Run> sampleRuns = request.getSamples().values().stream().flatMap(s -> s.getRuns().values().stream()).collect(Collectors.toSet());
        Set<Run> poolRuns = request.getPools().values().stream()
                .flatMap(s -> s.getRuns().values().stream()
                        .filter(r -> r.getPoolQcStatus() != QcStatus.UNDER_REVIEW))
                .collect(Collectors.toSet());

        if (poolRuns.size() > sampleRuns.size()) {
            for (Sample sample : request.getSamples().values()) {
                addPoolRunsToSample(request, sample);
            }
        }
    }

    private void addSampleInfo(Request request) {
        for (Sample sample : request.getValidNonPooledNormalSamples().values()) {
            LinkedHashMap<String, String> sampleInfo = getSampleInfoMap(sampleToDataRecord.get(sample), sample, request);
            sampleInfo.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(request.getId()));
            sample.setProperties(sampleInfo);
            sample.setIsTumor(sample.get(Constants.SAMPLE_CLASS) != null && !sample.get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL));
        }
    }

    private boolean validateSequencingRuns(Request request) {
        long numberOfSampleLevelQcs = 0;
        try {
            numberOfSampleLevelQcs = dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, "Request = '" + request.getId() + "'", user).size();
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }

        if (numberOfSampleLevelQcs == 0) {
            if (!forced && !request.isManualDemux()) {
                DEV_LOGGER.error("No sequencing runs found for this Request ID.");
                return false;
            } else if (request.isManualDemux()) {
                PM_LOGGER.log(PmLogPriority.WARNING, "MANUAL DEMULTIPLEXING was performed. Nothing but the request file should be output.");
                DEV_LOGGER.warn("MANUAL DEMULTIPLEXING was performed. Nothing but the request file should be output.");
            } else {
                PM_LOGGER.log(PmLogPriority.WARNING, "ALERT: There are no sequencing runs passing QC for this run. Force is true, I will pull ALL samples for this project.");
                DEV_LOGGER.warn("ALERT: There are no sequencing runs passing QC for this run. Force is true, I will pull ALL samples for this project.");
                request.setProcessingType(new ForcedProcessingType());
            }
        }
        return true;
    }

    private void setProperties(Request request, DataRecord dataRecordRequest) {
        request.setBicAutorunnable(getBoolean(dataRecordRequest, VeloxConstants.BIC_AUTORUNNABLE));
        request.setManualDemux(getBoolean(dataRecordRequest, VeloxConstants.MANUAL_DEMUX));
    }

    private boolean getBoolean(DataRecord dataRecordRequest, String fieldName) {
        try {
            return dataRecordRequest.getBooleanVal(fieldName, user);
        } catch (NullPointerException e) {
            return false;
        } catch (NotFound | RemoteException e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }

        return false;
    }

    private void setPairings(Request request, DataRecord dataRecordRequest) {
        try {
            for (Sample sample : request.getAllValidSamples().values()) {
                sample.setPairing(getPairingSample(sample, dataRecordRequest));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Sample getPairingSample(Sample sample, DataRecord requestDataRecord) throws IoError, RemoteException, NotFound {
        Sample pairing = null;
        List<DataRecord> records = Arrays.asList(requestDataRecord.getChildrenOfType(VeloxConstants.PAIRING_INFO, user));
        for (DataRecord record : records) {
            String tumorId = record.getStringVal(VeloxConstants.TUMOR_ID, user);
            String normalId = record.getStringVal(VeloxConstants.NORMAL_ID, user);

            if (Objects.equals(sample.getIgoId(), tumorId)) {
                //@TODO change to sampleType
//                sample.setIsTumor(true);
                pairing = new Sample(normalId);
            } else if (Objects.equals(sample.getIgoId(), normalId)) {
                pairing = new Sample(tumorId);
//                pairing.setIsTumor(true);
            }
        }
        return pairing;
    }

    private void processPooledNormals(Request request, DataRecord dataRecordRequest) {
        try {
            Map<DataRecord, Set<String>> pooledNormals = new LinkedHashMap<>(SampleInfoImpact.getPooledNormals());
            if (pooledNormals != null && pooledNormals.size() > 0) {
                DEV_LOGGER.info(String.format("Number of Pooled Normal Samples: %d", pooledNormals.size()));

                for (Map.Entry<DataRecord, Set<String>> pooledNormalToRuns : pooledNormals.entrySet()) {
                    DataRecord pooledNormalRecord = pooledNormalToRuns.getKey();
                    String cmoNormalId = pooledNormalRecord.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, user);
                    String igoNormalId = pooledNormalRecord.getStringVal(VeloxConstants.SAMPLE_ID, user);

                    Sample sample = request.putPooledNormalIfAbsent(igoNormalId);
                    sample.setCmoSampleId(cmoNormalId);
                    sample.setPooledNormal(true);
                    sample.setTransfer(false);
                    //@TODO check
                    sample.addRuns(getPooledNormalRuns(pooledNormalToRuns.getValue(), request));

                    Map<String, String> tempHashMap = getSampleInfoMap(pooledNormalRecord, sample, request);
                    tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(request.getId()));

                    // If include run ID is 'null' skip.
                    // This could mess up some older projects, so I may have to change this
                    if (tempHashMap.get(Constants.INCLUDE_RUN_ID) == null) {
                        logWarning("Skipping adding pooled normal info from " + tempHashMap.get(Constants.IGO_ID) + " because I cannot find include run id. ");
                        continue;
                    }

                    // If the sample pooled normal type (ex: FROZEN POOLED NORMAL) is already in the manfiest list
                    // Concatenate the include/ exclude run ids
                    //@TODO move to app side, combine in file generator
                    if (hasPooledNormal(request, cmoNormalId) && tempHashMap.get(Constants.INCLUDE_RUN_ID) != null) {
                        DEV_LOGGER.info(String.format("Combining Two Pooled Normals: %s", sample));

                        Map<String, String> originalPooledNormalSample = getPooledNormal(request, cmoNormalId).getProperties();
                        Set<String> currIncludeRuns = new TreeSet<>(Arrays.asList(originalPooledNormalSample.get(Constants.INCLUDE_RUN_ID).split(";")));

                        DEV_LOGGER.info(String.format("OLD include runs: %s", originalPooledNormalSample.get(Constants.INCLUDE_RUN_ID)));
                        Set<String> currExcludeRuns = new TreeSet<>(Arrays.asList(originalPooledNormalSample.get(Constants.EXCLUDE_RUN_ID).split(";")));

                        currIncludeRuns.addAll(Arrays.asList(tempHashMap.get(Constants.INCLUDE_RUN_ID).split(";")));
                        currExcludeRuns.addAll(Arrays.asList(originalPooledNormalSample.get(Constants.EXCLUDE_RUN_ID).split(";")));

                        tempHashMap.put(Constants.INCLUDE_RUN_ID, StringUtils.join(currIncludeRuns, ";"));
                        tempHashMap.put(Constants.EXCLUDE_RUN_ID, StringUtils.join(currExcludeRuns, ";"));
                    }

                    // Make sure samplesAndRuns has the corrected RUN IDs
                    List<String> runs = Arrays.asList(tempHashMap.get(Constants.INCLUDE_RUN_ID).split(";"));
                    Set<Run> runSet = runs.stream().map((r -> new Run(r))).filter(r -> !StringUtils.isEmpty(r.getId())).collect(Collectors.toSet());
                    //@TODO check
                    sample.addRuns(runSet);

                    //@TODO put anyway and check on app side
                    // If bait set does not contain comma, the add. Comma means that the pooled normal has two different bait sets. This shouldn't happen, So I'm not adding them.
                    String thisBait = tempHashMap.get(Constants.CAPTURE_BAIT_SET);
                    if (!thisBait.contains(",")) {
                        sample.setProperties(tempHashMap);
                    }

                    addPoolSeqQc(request, dataRecordRequest, Collections.singleton(pooledNormalRecord));
                    addPoolRunsToSample(request, sample);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private Sample getPooledNormal(Request request, String cmoNormalId) {
        return request.getSamples().values().stream().filter(s -> s.getCmoSampleId().equals(cmoNormalId)).findFirst().get();
    }

    private boolean hasPooledNormal(Request request, String cmoNormalId) {
        return request.getSamples().values().stream().anyMatch(s -> Objects.equals(s.getCmoSampleId(), cmoNormalId) && s.getProperties() != null && s.getProperties().size() > 0);
    }

    private Set<Run> getPooledNormalRuns(Set<String> pooledNormalPools, Request request) {
        Set<Run> runs = new HashSet<>();
        try {
            for (String pooledNormalPool : pooledNormalPools) {
                if (request.getPools().keySet().stream().anyMatch(p -> p.contains(pooledNormalPool))) {
                    Optional<Map.Entry<String, Pool>> pool = request.getPools().entrySet().stream().filter(p -> p.getKey().contains(pooledNormalPool)).findFirst();
                    if (pool.isPresent()) {
                        Collection<Run> poolRuns = pool.get().getValue().getRuns().values();
                        poolRuns.forEach(r -> r.setValid(true));
                        runs.addAll(poolRuns);
                    }
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }

        return runs;
    }

    private void setReqType(Request request) {
        if (StringUtils.isEmpty(request.getRequestType())) {
            // Here I will pull the childs field recipe
            List<Recipe> recipes = request.getRecipe();
            logWarning("RECIPE: " + getJoinedCollection(request.getRecipe(), ","));
            if (request.getName().matches("(.*)PACT(.*)")) {
                request.setRequestType(Constants.IMPACT);
            }

            if (recipes.size() == 1 && recipes.get(0) == Recipe.SMARTER_AMP_SEQ) {
                request.getLibTypes().add(LibType.SMARTER_AMPLIFICATION);
                request.getStrands().add(Strand.NONE);
                request.setRequestType(Constants.RNASEQ);

            }
            if (StringUtils.isEmpty(request.getRequestType())) {
                if (request.getId().startsWith(Constants.REQUEST_05500)) {
                    logWarning("05500 project. This should be pulled as an impact.");
                    request.setRequestType(Constants.IMPACT);
                } else if (runAsExome) {
                    request.setRequestType(Constants.EXOME);
                } else {
                    logWarning("Request Name doesn't match one of the supported request types: " + request.getName() + ". Information will be pulled as if it is an rnaseq/unknown run.");
                    request.setRequestType(Constants.OTHER);
                }
            }
        }
    }

    private void processSamples(Request request, DataRecord dataRecordRequest) {
        try {
            for (DataRecord dataRecordSample : dataRecordRequest.getChildrenOfType(VeloxConstants.SAMPLE, user)) {
                processSample(request, dataRecordSample);
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private void processSample(Request request, DataRecord dataRecordSample) throws NotFound, RemoteException, IoError {
        // Is this sample sequenced?
        String cmoSampleId = dataRecordSample.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, user);
        String igoSampleId = dataRecordSample.getStringVal(VeloxConstants.SAMPLE_ID, user);

        // Added because we were getting samples that had the same name as a sequenced sample, but then it was failed so it shouldn't be used (as per Aaron).
        String status = dataRecordSample.getSelectionVal(VeloxConstants.EXEMPLAR_SAMPLE_STATUS, user);
        if (status != Constants.FAILED_COMPLETED) {
            if (request.getSampleByCmoId(cmoSampleId).isPresent()) {
                logError("This request has two samples that have the same name: " + cmoSampleId);
            }

            Sample sample;
            if (isPool(cmoSampleId)) {
                Pool pool = request.putPoolIfAbsent(igoSampleId);
                sample = request.putSampleIfAbsent(igoSampleId);
                pool.setCmoSampleId(cmoSampleId);
                sample.setCmoSampleId(cmoSampleId);
            } else {
                sample = request.putSampleIfAbsent(igoSampleId);
                sample.setCmoSampleId(cmoSampleId);
                addSampleQcInformation(request, sample);
                addPostAnalysisQc(dataRecordSample, sample);
            }

            setIsTransfer(dataRecordSample, sample);
            sampleToDataRecord.put(sample, dataRecordSample);
        } else {
            DEV_LOGGER.warn(String.format("Skipping %s because the sample is failed: %s", cmoSampleId, status));
        }
    }

    private void setIsTransfer(DataRecord dataRecordSample, Sample sample) throws IoError, RemoteException {
        if (dataRecordSample.getParentsOfType(VeloxConstants.SAMPLE, user).size() > 0) {
            sample.setTransfer(true);
            DEV_LOGGER.warn(String.format("Sample: %s is a transfer from another request", sample));
        }
    }

    private boolean isPool(String cmoSampleId) {
        Predicate<String> isPoolPredicateByCmoId = (cmoId) -> cmoId.contains(",");
        return isPoolPredicateByCmoId.test(cmoSampleId);
    }

    private void processPlates(Request request, DataRecord dataRecordRequest) {
        try {
            for (DataRecord plate : dataRecordRequest.getChildrenOfType(VeloxConstants.PLATE, user)) {
                for (DataRecord dataRecordSample : plate.getChildrenOfType(VeloxConstants.SAMPLE, user)) {
                    processSample(request, dataRecordSample);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private void addSampleQcInformation(Request request, Sample sample) {
        try {
            List<DataRecord> sampleQCList = dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, "Request = '" + request.getId() + "' AND OtherSampleId = '" + sample.getCmoSampleId() + "'", user);

            if (sampleQCList.size() > 0) {
                for (DataRecord sampleQc : sampleQCList) {
                    String runStatus = String.valueOf(sampleQc.getPickListVal(VeloxConstants.SEQ_QC_STATUS, user));
                    String runID = getRunIdFromQcRecord(sampleQc);

                    Run run = sample.putRunIfAbsent(runID);
                    run.setRecordId(sampleQc.getRecordId());
                    QcStatus sampleLevelQcStatus = QcStatus.getByValue(runStatus);
                    run.setSampleLevelQcStatus(sampleLevelQcStatus);

                    if (sampleLevelQcStatus != QcStatus.PASSED)
                        run.setBadRun(true);

                    long totalReads = getTotalReads(sampleQc);
                    run.setNumberOfReads(totalReads);
                    sample.setNumberOfReads(sample.getNumberOfReads() + totalReads);

                    String alias = sampleQc.getStringVal(VeloxConstants.SAMPLE_ALIASES, user);
                    sample.setAlias(alias);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Sample specific QC for sample id: %s", sample.getIgoId()), e);
        }
    }

    private void addPoolRunsToSample(Request request, Sample sample) {
        Set<Pool> poolsWithCurrentSample = request.getPools().values().stream().filter(p -> p.getSamples().contains(sample)).collect(Collectors.toSet());
        for (Pool pool : poolsWithCurrentSample) {
            for (Run run : pool.getRuns().values()) {
                if (!sample.getRuns().values().contains(run))
                    sample.addRun(run);
            }
        }
    }

    private long getTotalReads(DataRecord sampleQc) throws NotFound, RemoteException {
        long totalReads = 0;
        try {
            totalReads = sampleQc.getLongVal(VeloxConstants.TOTAL_READS, user);
        } catch (NullPointerException skipped) {
        }

        return totalReads;
    }

    private String getRunIdFromQcRecord(DataRecord sampleQc) throws NotFound, RemoteException {
        String[] runParts = sampleQc.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, user).split("_");
        return runParts[0] + "_" + runParts[1];
    }

    private void addPostAnalysisQc(DataRecord dataRecordSample, Sample sample) {
        try {
            List<DataRecord> postQCs = dataRecordSample.getDescendantsOfType(VeloxConstants.POST_SEQ_ANALYSIS_QC, user);
            for (DataRecord postQc : postQCs) {
                String runID = getRunIdFromQcRecord(postQc);
                Run run = sample.putRunIfAbsent(runID);
                run.setPostQcStatus(QcStatus.getByValue(postQc.getPickListVal(VeloxConstants.POST_SEQ_QC_STATUS, user)));
                run.setNote(getRunNote(postQc));
                run.setRecordId(postQc.getRecordId());
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private String getRunNote(DataRecord postQc) throws NotFound, RemoteException {
        String note = postQc.getStringVal(VeloxConstants.NOTE, user);
        note = note.replaceAll("\n", " ");
        return note;
    }

    private void addPoolSeqQc(Request request, DataRecord dataRecordRequest, Collection<DataRecord> samplesToAddPoolQc) {
        try {
            ArrayList<DataRecord> sequencingRuns = new ArrayList<>(dataRecordRequest.getDescendantsOfType(VeloxConstants.SEQ_ANALYSIS_QC, user));
            for (DataRecord seqrun : sequencingRuns) {
                if (!verifySeqRun(seqrun, request.getId()))
                    continue;
                String status = String.valueOf(seqrun.getPickListVal(VeloxConstants.SEQ_QC_STATUS, user));

                if (StringUtils.isEmpty(status))
                    status = String.valueOf(seqrun.getEnumVal(VeloxConstants.QC_STATUS, user));

                String poolName = seqrun.getStringVal(VeloxConstants.SAMPLE_ID, user);
                String runId = getRunId(seqrun);
                List<DataRecord> samplesFromSeqRun = seqrun.getAncestorsOfType(VeloxConstants.SAMPLE, user);
                samplesFromSeqRun.retainAll(samplesToAddPoolQc);

                Pool pool = request.putPoolIfAbsent(poolName);
                for (DataRecord sampleRecord : samplesFromSeqRun) {
                    String igoSampleId = sampleRecord.getStringVal(VeloxConstants.SAMPLE_ID, user);
                    String cmoSampleId = sampleRecord.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, user);
                    Sample sample = request.getOrCreate(igoSampleId);
                    sample.setCmoSampleId(cmoSampleId);

                    pool.addSample(sample);
                    Run run = pool.putRunIfAbsent(runId);
                    run.setPoolQcStatus(QcStatus.getByValue(status));
                    run.setRecordId(seqrun.getRecordId());
                    Run sampleRun = sample.putRunIfAbsent(run);
                    sampleRun.setPoolQcStatus(QcStatus.getByValue(status));
                    sampleRun.setRecordId(seqrun.getRecordId());
                }
                linkPoolToRunID(runId, poolName);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Pool specific QC for request id: %s", Arguments.project), e);
        }
    }

    private void linkPoolToRunID(String RunID, String poolName) {
        Run.addRunIdPoolNameMapping(RunID, poolName);
    }

    private boolean verifySeqRun(DataRecord seqrun, String requestId) {
        // This is to verify this sequencing run is found
        // Get the parent sample data type
        // See what the request ID (s) are, search for reqID.
        // Return (is found?)

        boolean sameReq = false;

        try {
            List<DataRecord> parentSamples = seqrun.getParentsOfType(VeloxConstants.SAMPLE, user);
            for (DataRecord p : parentSamples) {
                String reqs = p.getStringVal(VeloxConstants.REQUEST_ID, user);
                List<String> requests = Arrays.asList(reqs.split("\\s*,\\s*"));
                sameReq = requests.contains(requestId);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while verifying sequence run for request id: %s", Arguments.project), e);
        }
        return sameReq;
    }

    private String getRunId(DataRecord seqrun) throws NotFound, RemoteException {
        // Try to get RunID, if not try to get pool name, if not again, then error!
        String RunID;
        String runPath = seqrun.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, user).replaceAll("/$", "");
        if (runPath.length() > 0) {
            String[] pathList = runPath.split("/");
            String runName = pathList[(pathList.length - 1)];
            String pattern = "^(\\d)+(_)([a-zA-Z]+_[\\d]{4})(_)([a-zA-Z\\d\\-_]+)";
            RunID = runName.replaceAll(pattern, "$3");
        } else {
            RunID = seqrun.getStringVal(VeloxConstants.SAMPLE_ID, user);
        }
        return RunID;
    }

    private void getLibTypes(Request request, DataRecord dataRecordRequest) {
        try {
            // ONE: Get ancestors of type sample from the passing seq Runs.
            List<Long> passingSeqRuns = getPassingSeqRuns(request, dataRecordRequest);
            List<List<DataRecord>> samplesFromSeqRun = dataRecordManager.getAncestorsOfTypeById(passingSeqRuns, VeloxConstants.SAMPLE, user);

            // TWO: Get decendants of type sample from the request
            List<DataRecord> samplesFromRequest = dataRecordRequest.getDescendantsOfType(VeloxConstants.SAMPLE, user);

            Set<DataRecord> finalSampleList = new HashSet<>();
            // THREE: Get the overlap
            for (List<DataRecord> sampList : samplesFromSeqRun) {
                ArrayList<DataRecord> temp = new ArrayList<>(sampList);
                temp.retainAll(samplesFromRequest);
                finalSampleList.addAll(temp);
            }

            if (request.isForced()) {
                finalSampleList.addAll(samplesFromRequest);
            }

            // Try finalSampleList FIRST. If this doesn't have any library types, just try samples from seq run.
            checkSamplesForLibTypes(request, finalSampleList);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Library Types for request id: %s", Arguments.project, e));
        }
    }

    private void checkSamplesForLibTypes(Request request, Set<DataRecord> finalSampleList) {
        try {
            // This is where I have to check all the overlapping samples for children of like 5 different types.
            for (DataRecord rec : finalSampleList) {
                List<DataRecord> truSeqRnaProtocolChildren = Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_PROTOCOL, user));
                if (checkValidBool(truSeqRnaProtocolChildren, dataRecordManager, user)) {
                    for (DataRecord rnaProtocol : truSeqRnaProtocolChildren) {
                        try {
                            if (getBoolean(rnaProtocol, VeloxConstants.VALID)) {
                                String exID = rnaProtocol.getStringVal(VeloxConstants.EXPERIMENT_ID, user);
                                List<DataRecord> rnaExp = dataRecordManager.queryDataRecords(VeloxConstants.TRU_SEQ_RNA_EXPERIMENT, "ExperimentId='" + exID + "'", user);
                                if (rnaExp.size() != 0) {
                                    List<Object> strandedness = dataRecordManager.getValueList(rnaExp, VeloxConstants.TRU_SEQ_STRANDING, user);
                                    for (Object x : strandedness) {
                                        // Only check for Stranded, because older kits were not stranded and did not have this field, ie null"
                                        if (String.valueOf(x).equals(Constants.STRANDED)) {
                                            request.addLibType(LibType.TRU_SEQ_POLY_A_SELECTION_STRANDED);
                                            request.addStrand(Strand.REVERSE);
                                        } else {
                                            request.addLibType(LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED);
                                            request.addStrand(Strand.NONE);
                                        }
                                        request.setRequestType(Constants.RNASEQ);
                                    }
                                }
                            }
                        } catch (NullPointerException e) {
                            String message = "You hit a null pointer exception while trying to find valid for library types. Please let BIC know.";
                            DEV_LOGGER.warn(message);
                            PM_LOGGER.warn(message);
                        } catch (Exception e) {
                            DEV_LOGGER.warn(String.format("Exception thrown while looking for valid for Library Types for request id: %s", Arguments.project), e);
                        }
                    }
                }
                if (Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_SM_RNA_PROTOCOL_4, user)).size() > 0) {
                    request.addLibType(LibType.TRU_SEQ_SM_RNA);
                    request.addStrand(Strand.EMPTY);
                    request.setRequestType(Constants.RNASEQ);
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RIBO_DEPLETE_PROTOCOL_1, user)), dataRecordManager, user)) {
                    request.addLibType(LibType.TRU_SEQ_RIBO_DEPLETE);
                    request.addStrand(Strand.REVERSE);
                    request.setRequestType(Constants.RNASEQ);
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_FUSION_PROTOCOL_1, user)), dataRecordManager, user)) {
                    request.addLibType(LibType.TRU_SEQ_FUSION_DISCOVERY);
                    request.addStrand(Strand.NONE);
                    request.setRequestType(Constants.RNASEQ);
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.SMAR_TER_AMPLIFICATION_PROTOCOL_1, user)), dataRecordManager, user)) {
                    request.addLibType(LibType.SMARTER_AMPLIFICATION);
                    request.addStrand(Strand.NONE);
                    request.setRequestType(Constants.RNASEQ);
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.KAPA_MRNA_STRANDED_SEQ_PROTOCOL_1, user)), dataRecordManager, user)) {
                    request.addLibType(LibType.KAPA_M_RNA_STRANDED);
                    request.addStrand(Strand.REVERSE);
                    request.setRequestType(Constants.RNASEQ);
                }
                if (rec.getChildrenOfType(VeloxConstants.NIMBLE_GEN_HYB_PROTOCOL, user).length != 0) {
                    request.setRequestType(Constants.IMPACT);
                }
                if (rec.getChildrenOfType(VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_1, user).length != 0) {
                    request.setRequestType(Constants.EXOME);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about protocols for request id: %s", Arguments.project), e);
        }
    }

    private Boolean checkValidBool(List<DataRecord> recs, DataRecordManager drm, User apiUser) {
        if (recs == null || recs.size() == 0) {
            return false;
        }

        try {
            List<Object> valids = drm.getValueList(recs, VeloxConstants.VALID, apiUser);
            for (Object val : valids) {
                if (String.valueOf(val).equals(VeloxConstants.TRUE)) {
                    return true;
                }
            }
        } catch (NullPointerException e) {
            String message = "You hit a null pointer exception while trying to find valid for library types. Please let BIC know.";
            PM_LOGGER.log(PmLogPriority.WARNING, message);
            DEV_LOGGER.warn(message);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while looking for valid for Library Types for request id: %s", Arguments.project), e);
        }

        return false;
    }

    private List<Long> getPassingSeqRuns(Request request, DataRecord dataRecordRequest) {
        List<Long> passingRuns = new ArrayList<>();
        try {
            List<DataRecord> sampleQCList = dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, "Request = '" + request.getId() + "'", user);
            for (DataRecord sampleQc : sampleQCList)
                passingRuns.add(sampleQc.getRecordId());

            ArrayList<DataRecord> sequencingRuns = new ArrayList<>(dataRecordRequest.getDescendantsOfType(VeloxConstants.SEQ_ANALYSIS_QC, user));
            for (DataRecord seqrun : sequencingRuns)
                passingRuns.add(seqrun.getRecordId());
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }

        return passingRuns;
    }

    private LinkedHashMap<String, String> getSampleInfoMap(DataRecord dataRecord, Sample sample, Request request) {
        LinkedHashMap<String, String> sampleInfoMap;
        // Latest attempt at refactoring the code. Why does species come up so much?
        SampleInfo sampleInfo;
        if (request.getRequestType().equals(Constants.IMPACT) || sample.isPooledNormal()) {
            sampleInfo = new SampleInfoImpact(user, dataRecordManager, dataRecord, request, sample);
        } else if (request.getRequestType().equals(Constants.EXOME)) {
            sampleInfo = new SampleInfoExome(user, dataRecordManager, dataRecord, request, sample);
        } else {
            sampleInfo = new SampleInfo(user, dataRecordManager, dataRecord, request, sample);
        }
        sampleInfoMap = sampleInfo.sendInfoToMap();

        if (sample.hasAlias()) {
            String alias = sample.getAlias();
            sampleInfoMap.put(Constants.MANIFEST_SAMPLE_ID, alias);
        }

        Set<Run> badRuns = sample.getBadRuns();
        String excludeRuns = StringUtils.join(badRuns, ";");
        sampleInfoMap.put(Constants.EXCLUDE_RUN_ID, excludeRuns);

        return sampleInfoMap;
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }

    public void logError(String message) {
        logError(message, Level.ERROR, Level.ERROR);
    }


    public void logError(String message, Level pmLogLevel, Level devLogLevel) {
        Utils.setExitLater(true);
        PM_LOGGER.log(pmLogLevel, message);
        DEV_LOGGER.log(devLogLevel, message);
    }

}
