package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.*;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.lims.SampleInfo;
import org.mskcc.kickoff.lims.SampleInfoExome;
import org.mskcc.kickoff.lims.SampleInfoImpact;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.SequencerIdentifierRetriever;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.util.VeloxConstants;

import java.rmi.RemoteException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.forced;
import static org.mskcc.kickoff.config.Arguments.runAsExome;

public class VeloxSingleRequestRetriever implements SingleRequestRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final User user;
    private final DataRecordManager dataRecordManager;

    private final Map<Sample, DataRecord> sampleToDataRecord = new HashMap<>();
    private final SequencerIdentifierRetriever sequencerIdentifierRetriever = new SequencerIdentifierRetriever();
    private ProjectInfoRetriever projectInfoRetriever;

    public VeloxSingleRequestRetriever(User user, DataRecordManager dataRecordManager, ProjectInfoRetriever
            projectInfoRetriever) {
        this.user = user;
        this.dataRecordManager = dataRecordManager;
        this.projectInfoRetriever = projectInfoRetriever;
    }

    @Override
    public KickoffRequest retrieve(String requestId, List<String> sampleIds, ProcessingType processingType) throws
            Exception {
        DEV_LOGGER.info(String.format("Retrieving information about request: %s", requestId));

        List<DataRecord> requestsDataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId " +
                "= '" + requestId + "'", user);
        if (requestsDataRecords != null && requestsDataRecords.size() > 0) {
            KickoffRequest kickoffRequest = new KickoffRequest(requestId, processingType);

            DataRecord dataRecordRequest = requestsDataRecords.get(0);

            List<DataRecord> originalSampRec = getOriginalSampleRecords(dataRecordRequest);

            setReadMe(kickoffRequest, dataRecordRequest);
            setName(kickoffRequest, dataRecordRequest);
            setRecipe(kickoffRequest, dataRecordRequest);
            setProperties(kickoffRequest, dataRecordRequest);

            getLibTypes(kickoffRequest, dataRecordRequest);
            setReqType(kickoffRequest);

            //@TODO cehck if I cant pool after process samples
            addPoolSeqQc(kickoffRequest, dataRecordRequest, originalSampRec);
            processPlates(kickoffRequest, dataRecordRequest);
            processSamples(kickoffRequest, dataRecordRequest, sampleIds);
            addPoolRunsToSamples(kickoffRequest);

            addSampleInfo(kickoffRequest);
            processPooledNormals(kickoffRequest, dataRecordRequest);
            kickoffRequest.setProjectInfo(getProjectInfo(kickoffRequest));

            return kickoffRequest;
        }
        throw new RuntimeException(String.format("Request: %s doesn't exist", requestId));
    }

    @Override
    public KickoffRequest retrieve(String requestId, ProcessingType processingType) throws Exception {
        List<DataRecord> requestsDataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId " +
                "= '" + requestId + "'", user);
        if (requestsDataRecords != null && requestsDataRecords.size() > 0) {
            List<DataRecord> samples = Arrays.asList(getAliquots(requestsDataRecords.get(0)));
            List<String> allSampleIds = new ArrayList<>();
            for (DataRecord sample : samples) {
                String igoId = sample.getStringVal(VeloxConstants.SAMPLE_ID, user);
                allSampleIds.add(igoId);
            }

            return retrieve(requestId, allSampleIds, processingType);
        }

        throw new RuntimeException(String.format("Request: %s doesn't exist", requestId));
    }

    private Map<String, String> getProjectInfo(KickoffRequest kickoffRequest) {
        Map<String, String> projectInfo = projectInfoRetriever.queryProjectInfo(user, dataRecordManager,
                kickoffRequest);
        kickoffRequest.setPi(projectInfoRetriever.getPI().split("@")[0]);
        kickoffRequest.setInvest(projectInfoRetriever.getInvest().split("@")[0]);

        return projectInfo;
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

    private void setReadMe(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        String readMe = "";
        try {
            readMe = dataRecordRequest.getStringVal(Constants.READ_ME, user);
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }
        kickoffRequest.setReadMe(readMe);
    }

    private void setName(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        String requestName = "";
        try {
            requestName = dataRecordRequest.getPickListVal(Constants.REQUEST_NAME, user);
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }
        kickoffRequest.setName(requestName);
    }

    private void setRecipe(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        List<Object> recipes = new ArrayList<>();

        try {
            List<DataRecord> samples = Arrays.asList(getAliquots(dataRecordRequest));
            recipes = dataRecordManager.getValueList(samples, VeloxConstants.RECIPE, user);
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }

        try {
            List<Recipe> recipeList = recipes.stream()
                    .map(r -> Recipe.getRecipeByValue(r.toString()))
                    .distinct()
                    .collect(Collectors.toList());

            if (recipeList.size() == 1)
                kickoffRequest.setRecipe(recipeList.get(0));
        } catch (Recipe.UnsupportedRecipeException | Recipe.EmptyRecipeException e) {
            DEV_LOGGER.warn(String.format("Invalid recipe for request %s: %s", kickoffRequest.getId(), e.getMessage()
            ), e);
        }
    }

    private void addPoolRunsToSamples(KickoffRequest kickoffRequest) {
        Set<Run> sampleRuns = kickoffRequest.getSamples().values().stream()
                .flatMap(s -> s.getRuns()
                        .values().stream())
                .collect(Collectors.toSet());

        Set<Run> poolRuns = kickoffRequest.getPools().values().stream()
                .flatMap(s -> s.getRuns().values().stream()
                        .filter(r -> r.getPoolQcStatus() != QcStatus.UNDER_REVIEW))
                .collect(Collectors.toSet());

        if (poolRuns.size() > sampleRuns.size()) {
            for (Sample sample : kickoffRequest.getSamples().values()) {
                addPoolRunsToSample(kickoffRequest, sample);
            }
        }
    }

    private void addSampleInfo(KickoffRequest kickoffRequest) {
        for (Sample sample : kickoffRequest.getValidNonPooledNormalSamples().values()) {
            LinkedHashMap<String, String> sampleInfo = getSampleInfoMap(sampleToDataRecord.get(sample), sample,
                    kickoffRequest);
            sampleInfo.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(kickoffRequest.getId()));
            sample.setProperties(sampleInfo);
            sample.setIsTumor(sample.get(Constants.SAMPLE_CLASS) != null && !sample.get(Constants.SAMPLE_CLASS)
                    .contains(Constants.NORMAL));
        }
    }

    private void setProperties(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        kickoffRequest.setBicAutorunnable(getBoolean(dataRecordRequest, VeloxConstants.BIC_AUTORUNNABLE));
        kickoffRequest.setManualDemux(getBoolean(dataRecordRequest, VeloxConstants.MANUAL_DEMUX));
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

    private void processPooledNormals(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        try {
            Map<DataRecord, Collection<String>> pooledNormals = new LinkedHashMap<>(SampleInfoImpact.getPooledNormals
                    ());
            if (pooledNormals.size() > 0) {
                DEV_LOGGER.info(String.format("Number of Pooled Normal Samples: %d", pooledNormals.size()));

                for (Map.Entry<DataRecord, Collection<String>> pooledNormalToRuns : pooledNormals.entrySet()) {
                    DataRecord pooledNormalRecord = pooledNormalToRuns.getKey();
                    String cmoNormalId = pooledNormalRecord.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, user);
                    String igoNormalId = pooledNormalRecord.getStringVal(VeloxConstants.SAMPLE_ID, user);

                    Sample sample = kickoffRequest.putPooledNormalIfAbsent(igoNormalId);
                    sample.setCmoSampleId(cmoNormalId);
                    sample.setPooledNormal(true);
                    sample.setTransfer(false);
                    sample.setIsTumor(false);
                    sample.addRuns(getPooledNormalRuns(pooledNormalToRuns.getValue(), kickoffRequest));

                    Map<String, String> tempHashMap = getSampleInfoMap(pooledNormalRecord, sample, kickoffRequest);
                    tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(kickoffRequest.getId()));

                    // If include run ID is 'null' skip.
                    // This could mess up some older projects, so I may have to change this
                    if (tempHashMap.get(Constants.INCLUDE_RUN_ID) == null) {
                        logWarning("Skipping adding pooled normal info from " + tempHashMap.get(Constants.IGO_ID) + " because I cannot find include run id. ");
                        continue;
                    }

                    // If the sample pooled normal type (ex: FROZEN POOLED NORMAL) is already in the manfiest list
                    // Concatenate the include/ exclude run ids
                    //@TODO move to app side, combine in file generator
                    if (hasPooledNormal(kickoffRequest, cmoNormalId) && tempHashMap.get(Constants.INCLUDE_RUN_ID) !=
                            null) {
                        DEV_LOGGER.info(String.format("Combining Two Pooled Normals: %s", sample));

                        Map<String, String> originalPooledNormalSample = getPooledNormal(kickoffRequest, cmoNormalId)
                                .getProperties();
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
                    Set<Run> runSet = runs.stream().map((Run::new)).filter(r -> !StringUtils.isEmpty(r.getId())).collect(Collectors.toSet());
                    //@TODO check
                    sample.addRuns(runSet);

                    //@TODO put anyway and check on app side
                    // If bait set does not contain comma, the add. Comma means that the pooled normal has two different bait sets. This shouldn't happen, So I'm not adding them.
                    String thisBait = tempHashMap.get(Constants.CAPTURE_BAIT_SET);
                    if (!thisBait.contains(",")) {
                        sample.setProperties(tempHashMap);
                    }

                    addPoolSeqQc(kickoffRequest, dataRecordRequest, Collections.singleton(pooledNormalRecord));
                    addPoolRunsToSample(kickoffRequest, sample);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private Sample getPooledNormal(KickoffRequest kickoffRequest, String cmoNormalId) {
        return kickoffRequest.getSamples().values().stream().filter(s -> s.getCmoSampleId().equals(cmoNormalId))
                .findFirst().get();
    }

    private boolean hasPooledNormal(KickoffRequest kickoffRequest, String cmoNormalId) {
        return kickoffRequest.getSamples().values().stream().anyMatch(s -> Objects.equals(s.getCmoSampleId(),
                cmoNormalId) && s.getProperties() != null && s.getProperties().size() > 0);
    }

    private Set<Run> getPooledNormalRuns(Collection<String> pooledNormalPools, KickoffRequest kickoffRequest) {
        Set<Run> runs = new HashSet<>();
        try {
            for (String pooledNormalPool : pooledNormalPools) {
                if (kickoffRequest.getPools().keySet().stream().anyMatch(p -> p.contains(pooledNormalPool))) {
                    Optional<Map.Entry<String, Pool>> pool = kickoffRequest.getPools().entrySet().stream().filter(p
                            -> p.getKey().contains(pooledNormalPool)).findFirst();
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

    private void setReqType(KickoffRequest kickoffRequest) {
        if (kickoffRequest.getRequestType() == null) {
            // Here I will pull the childs field recipe
            Recipe recipe = kickoffRequest.getRecipe();
            logWarning(String.format("RECIPE for request %s is: %s", kickoffRequest.getId(), kickoffRequest.getRecipe
                    ()));
            if (kickoffRequest.getName().matches("(.*)PACT(.*)")) {
                kickoffRequest.setRequestType(RequestType.IMPACT);
            }

            if (isSmarterAmpSeqRecipe(recipe)) {
                kickoffRequest.getLibTypes().add(LibType.SMARTER_AMPLIFICATION);
                kickoffRequest.getStrands().add(Strand.NONE);
                kickoffRequest.setRequestType(RequestType.RNASEQ);
            }
            if (kickoffRequest.getRequestType() == null) {
                if (kickoffRequest.isInnovation()) {
                    logWarning("05500 project. This should be pulled as an impact.");
                    kickoffRequest.setRequestType(RequestType.IMPACT);
                } else if (runAsExome) {
                    kickoffRequest.setRequestType(RequestType.EXOME);
                } else {
                    logWarning("Request Name doesn't match one of the supported request types: " + kickoffRequest
                            .getName() + ". Information will be pulled as if it is an rnaseq/unknown run.");
                    kickoffRequest.setRequestType(RequestType.OTHER);
                }
            }
        }
    }

    private boolean isSmarterAmpSeqRecipe(Recipe recipe) {
        return recipe == Recipe.SMARTER_AMP_SEQ;
    }

    private void processSamples(KickoffRequest kickoffRequest, DataRecord dataRecordRequest, List<String> sampleIds) {
        try {
            List<DataRecord> childSamplesToProcess = new ArrayList<>();
            List<DataRecord> childSamples = new LinkedList<>(Arrays.asList(getAliquots(dataRecordRequest)));
            for (DataRecord childSample : childSamples) {
                String igoId = childSample.getStringVal(VeloxConstants.SAMPLE_ID, user);
                if (sampleIds.contains(igoId))
                    childSamplesToProcess.add(childSample);
            }

            for (DataRecord dataRecordSample : childSamplesToProcess) {
                processSample(kickoffRequest, dataRecordSample);
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private void processSample(KickoffRequest kickoffRequest, DataRecord dataRecordSample) throws Exception {
        // Is this sample sequenced?
        String cmoSampleId = dataRecordSample.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, user);
        String igoSampleId = dataRecordSample.getStringVal(VeloxConstants.SAMPLE_ID, user);

        // Added because we were getting samples that had the same name as a sequenced sample, but then it was failed so it shouldn't be used (as per Aaron).
        String status = dataRecordSample.getSelectionVal(VeloxConstants.EXEMPLAR_SAMPLE_STATUS, user);
        if (!Objects.equals(status, Constants.FAILED_COMPLETED)) {
            Sample sample;
            if (isPool(cmoSampleId)) {
                Pool pool = kickoffRequest.putPoolIfAbsent(igoSampleId);
                sample = kickoffRequest.putSampleIfAbsent(igoSampleId);
                pool.setCmoSampleId(cmoSampleId);
                sample.setCmoSampleId(cmoSampleId);
            } else {
                sample = kickoffRequest.putSampleIfAbsent(igoSampleId);
                sample.setCmoSampleId(cmoSampleId);
                sample.setRequestId(kickoffRequest.getId());
                addSampleQcInformation(kickoffRequest, sample);
                addPostAnalysisQc(dataRecordSample, sample);
            }

            setIsTransfer(dataRecordSample, sample);
            sampleToDataRecord.put(sample, dataRecordSample);
            setSeqName(dataRecordSample, sample);
        } else {
            DEV_LOGGER.warn(String.format("Skipping %s because the sample is failed: %s", cmoSampleId, status));
        }
    }

    private void setSeqName(DataRecord dataRecordSample, Sample sample) throws Exception {
        DataRecord[] sampleLevelQcs = getSampleLevelQcs(dataRecordSample);
        for (DataRecord sampleLevelQc : sampleLevelQcs) {
            if (isPassed(sampleLevelQc))
                sample.setSeqName(getSeqName(sampleLevelQc));
        }

        if (sampleLevelQcFound(sampleLevelQcs))
            return;

        DataRecord[] aliquots = getAliquots(dataRecordSample);
        for (DataRecord aliquot : aliquots) {
            if (isFromSameRequest(aliquot))
                setSeqName(aliquot, sample);
        }
    }

    private DataRecord[] getAliquots(DataRecord dataRecordSample) throws IoError, RemoteException {
        return dataRecordSample.getChildrenOfType(VeloxConstants.SAMPLE, user);
    }

    private DataRecord[] getSampleLevelQcs(DataRecord dataRecordSample) throws IoError, RemoteException {
        return dataRecordSample.getChildrenOfType(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, user);
    }

    private boolean sampleLevelQcFound(DataRecord[] sampleLevelQcs) {
        return sampleLevelQcs.length > 0;
    }

    private String getSeqName(DataRecord sampleLevelQc) throws NotFound, RemoteException {
        String runFolder = sampleLevelQc.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, user);

        return sequencerIdentifierRetriever.retrieve(runFolder);
    }

    private boolean isPassed(DataRecord sampleLevelQc) throws Exception {
        String qcStatusString = sampleLevelQc.getStringVal(VeloxConstants.SEQ_QC_STATUS, user);
        QcStatus qcStatus = QcStatus.getByValue(qcStatusString);
        return qcStatus != QcStatus.FAILED && qcStatus != QcStatus.UNDER_REVIEW;
    }

    private boolean isFromSameRequest(DataRecord dataRecordSample) throws Exception {
        List<DataRecord> parentRequests = dataRecordSample.getParentsOfType(VeloxConstants.REQUEST, user);
        return parentRequests.size() == 0;
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

    private void processPlates(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        try {
            for (DataRecord plate : dataRecordRequest.getChildrenOfType(VeloxConstants.PLATE, user)) {
                for (DataRecord dataRecordSample : getAliquots(plate)) {
                    processSample(kickoffRequest, dataRecordSample);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error(e.getMessage(), e);
        }
    }

    private void addSampleQcInformation(KickoffRequest kickoffRequest, Sample sample) {
        try {
            List<DataRecord> sampleQCList = dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC,
                    "Request = '" + kickoffRequest.getId() + "' AND OtherSampleId = '" + sample.getCmoSampleId() +
                            "'", user);

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
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Sample specific QC for" +
                    " sample id: %s", sample.getIgoId()), e);
        }
    }

    private void addPoolRunsToSample(KickoffRequest kickoffRequest, Sample sample) {
        Set<Pool> poolsWithCurrentSample = kickoffRequest.getPools().values().stream().filter(p -> p.getSamples()
                .contains(sample)).collect(Collectors.toSet());
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
            DEV_LOGGER.warn(String.format("NPE thrown while retrieving Total Reads for record sample qc: %d", sampleQc.getRecordId()));
        }

        return totalReads;
    }

    private String getRunIdFromQcRecord(DataRecord sampleQc) throws NotFound, RemoteException {
        String[] runParts = sampleQc.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, user).split("_");
        return String.format("%s_%s", runParts[0], runParts[1]);
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

    private void addPoolSeqQc(KickoffRequest kickoffRequest, DataRecord dataRecordRequest, Collection<DataRecord>
            samplesToAddPoolQc) {
        try {
            ArrayList<DataRecord> sequencingRuns = new ArrayList<>(dataRecordRequest.getDescendantsOfType(VeloxConstants.SEQ_ANALYSIS_QC, user));
            for (DataRecord seqrun : sequencingRuns) {
                if (!verifySeqRun(seqrun, kickoffRequest.getId()))
                    continue;
                String status = String.valueOf(seqrun.getPickListVal(VeloxConstants.SEQ_QC_STATUS, user));

                if (StringUtils.isEmpty(status))
                    status = String.valueOf(seqrun.getEnumVal(VeloxConstants.QC_STATUS, user));

                String poolName = seqrun.getStringVal(VeloxConstants.SAMPLE_ID, user);
                String runId = getRunId(seqrun);
                List<DataRecord> samplesFromSeqRun = seqrun.getAncestorsOfType(VeloxConstants.SAMPLE, user);
                samplesFromSeqRun.retainAll(samplesToAddPoolQc);

                Pool pool = kickoffRequest.putPoolIfAbsent(poolName);
                for (DataRecord sampleRecord : samplesFromSeqRun) {
                    String igoSampleId = sampleRecord.getStringVal(VeloxConstants.SAMPLE_ID, user);
                    String cmoSampleId = sampleRecord.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, user);
                    Sample sample = kickoffRequest.getOrCreate(igoSampleId);
                    sample.setCmoSampleId(cmoSampleId);

                    pool.addSample(sample);
                    Run run = pool.putRunIfAbsent(runId);
                    run.setPoolQcStatus(QcStatus.getByValue(status));
                    run.setRecordId(seqrun.getRecordId());
                    Run sampleRun = sample.putRunIfAbsent(run.getId());
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

    private void getLibTypes(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        try {
            // ONE: Get ancestors of type sample from the passing seq Runs.
            List<Long> passingSeqRuns = getPassingSeqRuns(kickoffRequest, dataRecordRequest);
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

            if (kickoffRequest.isForced()) {
                finalSampleList.addAll(samplesFromRequest);
            }

            // Try finalSampleList FIRST. If this doesn't have any library types, just try samples from seq run.
            checkSamplesForLibTypes(kickoffRequest, finalSampleList);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about Library Types for " +
                    "request id: %s", Arguments.project), e);
        }
    }

    private void checkSamplesForLibTypes(KickoffRequest kickoffRequest, Set<DataRecord> finalSampleList) {
        try {
            for (DataRecord sampleRecord : finalSampleList) {
                List<DataRecord> truSeqRnaProtocolChildren = Arrays.asList(sampleRecord.getChildrenOfType
                        (VeloxConstants.TRU_SEQ_RNA_PROTOCOL, user));
                if (checkValidBool(truSeqRnaProtocolChildren, dataRecordManager, user))
                    for (DataRecord rnaProtocol : truSeqRnaProtocolChildren)
                        setLibAndStrandForProtocol(kickoffRequest, rnaProtocol);
                setLibAndStrandForSample(kickoffRequest, sampleRecord);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving information about protocols for request " +
                    "id: %s", Arguments.project), e);
        }
    }

    private void setLibAndStrandForSample(KickoffRequest kickoffRequest, DataRecord rec) throws IoError,
            RemoteException {
        if (Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_SM_RNA_PROTOCOL_4, user)).size() > 0) {
            kickoffRequest.addLibType(LibType.TRU_SEQ_SM_RNA);
            kickoffRequest.addStrand(Strand.EMPTY);
            kickoffRequest.setRequestType(RequestType.RNASEQ);
        }
        if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RIBO_DEPLETE_PROTOCOL_1, user))
                , dataRecordManager, user)) {
            kickoffRequest.addLibType(LibType.TRU_SEQ_RIBO_DEPLETE);
            kickoffRequest.addStrand(Strand.REVERSE);
            kickoffRequest.setRequestType(RequestType.RNASEQ);
        }
        if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_FUSION_PROTOCOL_1, user)),
                dataRecordManager, user)) {
            kickoffRequest.addLibType(LibType.TRU_SEQ_FUSION_DISCOVERY);
            kickoffRequest.addStrand(Strand.NONE);
            kickoffRequest.setRequestType(RequestType.RNASEQ);
        }
        if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.SMAR_TER_AMPLIFICATION_PROTOCOL_1,
                user)), dataRecordManager, user)) {
            kickoffRequest.addLibType(LibType.SMARTER_AMPLIFICATION);
            kickoffRequest.addStrand(Strand.NONE);
            kickoffRequest.setRequestType(RequestType.RNASEQ);
        }
        if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.KAPA_MRNA_STRANDED_SEQ_PROTOCOL_1,
                user)), dataRecordManager, user)) {
            kickoffRequest.addLibType(LibType.KAPA_M_RNA_STRANDED);
            kickoffRequest.addStrand(Strand.REVERSE);
            kickoffRequest.setRequestType(RequestType.RNASEQ);
        }
        if (rec.getChildrenOfType(VeloxConstants.NIMBLE_GEN_HYB_PROTOCOL, user).length != 0) {
            kickoffRequest.setRequestType(RequestType.IMPACT);
        }
        if (rec.getChildrenOfType(VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_1, user).length != 0) {
            kickoffRequest.setRequestType(RequestType.EXOME);
        }
    }

    private void setLibAndStrandForProtocol(KickoffRequest kickoffRequest, DataRecord rnaProtocol) {
        try {
            if (getBoolean(rnaProtocol, VeloxConstants.VALID)) {
                String exID = rnaProtocol.getStringVal(VeloxConstants.EXPERIMENT_ID, user);
                List<DataRecord> rnaExp = dataRecordManager.queryDataRecords(VeloxConstants.TRU_SEQ_RNA_EXPERIMENT,
                        "ExperimentId='" + exID + "'", user);
                if (rnaExp.size() != 0) {
                    List<Object> strandedness = dataRecordManager.getValueList(rnaExp, VeloxConstants
                            .TRU_SEQ_STRANDING, user);
                    for (Object x : strandedness) {
                        // Only check for Stranded, because older kits were not stranded and did not have this field,
                        // ie null"
                        if (String.valueOf(x).equals(Constants.STRANDED)) {
                            kickoffRequest.addLibType(LibType.TRU_SEQ_POLY_A_SELECTION_STRANDED);
                            kickoffRequest.addStrand(Strand.REVERSE);
                        } else {
                            kickoffRequest.addLibType(LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED);
                            kickoffRequest.addStrand(Strand.NONE);
                        }
                        kickoffRequest.setRequestType(RequestType.RNASEQ);
                    }
                }
            }
        } catch (NullPointerException e) {
            String message = "You hit a null pointer exception while trying to find valid for library types. Please " +
                    "let BIC know.";
            DEV_LOGGER.warn(message);
            PM_LOGGER.warn(message);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while looking for valid for Library Types for request id: %s", Arguments.project), e);
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
            String message = "You hit a null pointer exception while trying to find valid for library types. Please " +
                    "let BIC know.";
            PM_LOGGER.log(PmLogPriority.WARNING, message);
            DEV_LOGGER.warn(message);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while looking for valid for Library Types for request id:" +
                    " %s", Arguments.project), e);
        }

        return false;
    }

    private List<Long> getPassingSeqRuns(KickoffRequest kickoffRequest, DataRecord dataRecordRequest) {
        List<Long> passingRuns = new ArrayList<>();
        try {
            List<DataRecord> sampleQCList = dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC,
                    "Request = '" + kickoffRequest.getId() + "'", user);

            if (sampleQCList.size() == 0 && forced && !kickoffRequest.isManualDemux())
                kickoffRequest.setProcessingType(new ForcedProcessingType());

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

    private LinkedHashMap<String, String> getSampleInfoMap(DataRecord dataRecord, Sample sample, KickoffRequest
            kickoffRequest) {
        LinkedHashMap<String, String> sampleInfoMap;
        // Latest attempt at refactoring the code. Why does species come up so much?
        SampleInfo sampleInfo;
        if (kickoffRequest.getRequestType() == RequestType.IMPACT || sample.isPooledNormal()) {
            sampleInfo = new SampleInfoImpact(user, dataRecordManager, dataRecord, kickoffRequest, sample);
        } else if (kickoffRequest.getRequestType() == RequestType.EXOME) {
            sampleInfo = new SampleInfoExome(user, dataRecordManager, dataRecord, kickoffRequest, sample);
        } else {
            sampleInfo = new SampleInfo(user, dataRecordManager, dataRecord, kickoffRequest, sample);
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
