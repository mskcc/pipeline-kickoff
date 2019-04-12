package org.mskcc.kickoff.poolednormals;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.retriever.NimblegenResolver;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.velox.Sample2DataRecordMap;
import org.mskcc.util.VeloxConstants;

import java.rmi.RemoteException;
import java.util.*;

public class ImpactExomePooledNormalsRetriever implements PooledNormalsRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private NimblegenResolver nimblegenResolver;

    private List<DataRecord> potentialPooledNormalsQcs;
    private Sample2DataRecordMap sample2DataRecordMap;

    public ImpactExomePooledNormalsRetriever(NimblegenResolver nimblegenResolver, Sample2DataRecordMap
            sample2DataRecordMap) {
        this.nimblegenResolver = nimblegenResolver;
        this.sample2DataRecordMap = sample2DataRecordMap;
    }

    @Override
    public Map<DataRecord, Collection<String>> getAllPooledNormals(KickoffRequest request, User user,
                                                                   DataRecordManager dataRecordManager) {
        Map<DataRecord, Collection<String>> pooledNormals = getPooledNormalsFromNiblegen(user, request,
                dataRecordManager);

        SetMultimap<DataRecord, String> pooledNormalsFromQc = getPooledNormalsFromQc(user, dataRecordManager,
                request);

        pooledNormals.putAll(pooledNormalsFromQc.asMap());
        return pooledNormals;
    }

    private Map<DataRecord, Collection<String>> getPooledNormalsFromNiblegen(User user, KickoffRequest
            kickoffRequest, DataRecordManager dataRecordManager) {

        SetMultimap<DataRecord, String> pooledNormals = HashMultimap.create();

        try {
            for (Sample sample : kickoffRequest.getValidNonPooledNormalSamples().values()) {
                DataRecord sampleRec = sample2DataRecordMap.get(sample.getIgoId());
                DataRecord nimblegenRecord = nimblegenResolver.resolve(dataRecordManager, sampleRec, user, sample
                        .isPooledNormal());
                DataRecord nimbParentSample = nimblegenRecord.getParentsOfType(VeloxConstants.SAMPLE, user).get(0);
                List<DataRecord> nimbSiblingSamples = Arrays.asList(nimbParentSample.getChildrenOfType(VeloxConstants
                        .SAMPLE, user));
                for (DataRecord nimbSiblingSample : nimbSiblingSamples) {
                    // HERE check tos ee if it was added ot a flowcell?
                    List<DataRecord> flowCellLanes = nimbSiblingSample.getDescendantsOfType(VeloxConstants
                            .FLOW_CELL_LANE, user);

                    if (flowCellLanes == null || flowCellLanes.size() == 0)
                        continue;

                    List<DataRecord> parentSamples = nimbSiblingSample.getParentsOfType(VeloxConstants.SAMPLE, user);
                    for (DataRecord parentSample : parentSamples) {
                        if (isPooledNormal(user, parentSample)) {
                            String pooledNormalId = nimbSiblingSample.getStringVal(VeloxConstants.SAMPLE_ID, user);

                            if (!pooledNormals.containsKey(parentSample))
                                DEV_LOGGER.info(String.format("Adding pooled normal: %s", pooledNormalId));
                            pooledNormals.put(parentSample, pooledNormalId);
                        }
                    }
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error("Exception thrown while retrieving information about pooled normals", e);
        }

        return pooledNormals.asMap();
    }

    private boolean isPooledNormal(User apiUser, DataRecord parentSample) throws NotFound, RemoteException {
        return parentSample.getStringVal(VeloxConstants.SAMPLE_ID, apiUser).startsWith("CTRL")
                || parentSample.getStringVal(org.mskcc.util.VeloxConstants.OTHER_SAMPLE_ID, apiUser).contains("POOLEDNORMAL");
    }
  
    private boolean isSampleRun(DataRecord potentialPooledNormalQc, User apiUser, KickoffRequest kickoffRequest)
            throws NotFound, RemoteException {
        String sampleId = potentialPooledNormalQc.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);

        try {
            String runFolder = potentialPooledNormalQc.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, apiUser);

            boolean isSampleRun = kickoffRequest.getAllValidSamples().values().stream()
                    .flatMap(s -> s.getValidRunIds().stream())
                    .anyMatch(r -> runFolder.startsWith(r));

            return isSampleRun;
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Error while trying to get pooled normal run id: %s. This pooled normal " +
                    "won't be added.", sampleId), e);
            return false;
        }
    }

    public SetMultimap<DataRecord, String> getPooledNormalsFromQc(User apiUser, DataRecordManager
            dataRecordManager, KickoffRequest kickoffRequest) {
        SetMultimap<DataRecord, String> pooledNormals = HashMultimap.create();

        try {
            if (potentialPooledNormalsQcs == null)
                potentialPooledNormalsQcs = getPotentialPooledNormalQCs(apiUser, dataRecordManager);

            for (DataRecord potentialPooledNormalQc : potentialPooledNormalsQcs) {
                if (!isSampleRun(potentialPooledNormalQc, apiUser, kickoffRequest))
                    continue;

                List<DataRecord> parentSamples = potentialPooledNormalQc.getParentsOfType(VeloxConstants.SAMPLE,
                        apiUser);

                if (parentSamples.size() == 0) {
                    DEV_LOGGER.warn(String.format("No parent sample for Sample Level Qc %s. This Pooled normal won't " +
                                    "be added.",
                            potentialPooledNormalQc.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser)));
                    continue;
                }

                if (parentSamples.size() > 1) {
                    DEV_LOGGER.warn(String.format("Multiple parent samples for Sample Level Qc %s. This Pooled normal" +
                                    " won't be " +
                                    "added.",
                            potentialPooledNormalQc.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser)));
                    continue;
                }

                DataRecord parentSample = parentSamples.get(0);
                String otherSampleId = parentSample.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);

                String pooledNormalRecipe = parentSample.getStringVal(VeloxConstants.RECIPE, apiUser);
                if (StringUtils.isEmpty(pooledNormalRecipe)) {
                    DEV_LOGGER.warn(String.format("Empty recipe for sample: %s", otherSampleId));
                }

                boolean isSameRecipe = Objects.equals(pooledNormalRecipe, kickoffRequest.getRecipe().getValue());

                if (!isSameRecipe)
                    continue;

                if (!isSamePreservation(kickoffRequest, otherSampleId))
                    continue;

                if (isPooledNormal(apiUser, parentSample)) {
                    String pooledNormalId = parentSample.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);
                    if (!pooledNormals.containsKey(parentSample))
                        DEV_LOGGER.info(String.format("Adding pooled normal id %s for pooled normal %s", pooledNormalId,
                                otherSampleId));

                    pooledNormals.put(parentSample, pooledNormalId);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error("Exception thrown wile retrieving information about pooled normals", e);
        }

        return pooledNormals;
    }

    private boolean isSamePreservation(KickoffRequest kickoffRequest, String otherSampleId) {
        if (StringUtils.isEmpty(otherSampleId))
            return false;

        return kickoffRequest.getSamples().values().stream()
                .map(s -> s.getPreservation())
                .anyMatch(s -> otherSampleId.equalsIgnoreCase(s));
    }

    private List<DataRecord> getPotentialPooledNormalQCs(User apiUser, DataRecordManager dataRecordManager)
            throws Exception {
        String query = String.format("%s LIKE '%s' OR %s LIKE '%s'", VeloxConstants.OTHER_SAMPLE_ID, "%POOLEDNORMAL%",
                VeloxConstants.SAMPLE_ID, "CTRL%");

        DEV_LOGGER.info(String.format("Query used to look for pooled normals: %s", query));
        return dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC,
                query, apiUser);
    }
}
