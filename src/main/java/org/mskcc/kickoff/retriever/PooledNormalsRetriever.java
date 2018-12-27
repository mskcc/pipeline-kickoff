package org.mskcc.kickoff.retriever;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.SampleInfoImpact;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.util.VeloxConstants;

import java.rmi.RemoteException;
import java.util.*;
import java.util.stream.Collectors;

public class PooledNormalsRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private static List<DataRecord> potentialPooledNormalsQcs;

    public static boolean isPooledNormal(User apiUser, DataRecord parentSample) throws NotFound, RemoteException {
        return parentSample.getStringVal(VeloxConstants.SAMPLE_ID, apiUser).startsWith("CTRL");
    }

    private static boolean isSampleRun(DataRecord potentialPooledNormalQc, User apiUser, KickoffRequest kickoffRequest)
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

    public static SetMultimap<DataRecord, String> populatePoolNormals(User apiUser, DataRecordManager
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

                String pooledNormalRecipe = parentSample.getStringVal(VeloxConstants.RECIPE, apiUser);

                boolean isSameRecipe = Objects.equals(pooledNormalRecipe, kickoffRequest.getRecipe().getValue());

                if (StringUtils.isEmpty(pooledNormalRecipe))
                    DEV_LOGGER.warn("empty recipe: " + parentSample.getRecordId());
                if (!isSameRecipe)
                    continue;

                String otherSampleId = parentSample.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);

                Set<String> samplesPreservations = kickoffRequest.getSamples().values().stream()
                        .map(s -> s.getPreservation())
                        .collect(Collectors.toSet());

                boolean isSamePreservation = samplesPreservations.stream()
                        .anyMatch(s -> otherSampleId.toLowerCase().startsWith(s.toLowerCase
                                ()));

                if (!isSamePreservation)
                    continue;

                if (isPooledNormal(apiUser, parentSample)) {
                    String pooledNormalId = parentSample.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);
                    if (!pooledNormals.containsKey(parentSample))
                        DEV_LOGGER.info(String.format("Adding pooled normal: %s", pooledNormalId));

                    pooledNormals.put(parentSample, pooledNormalId);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.error("Exception thrown wile retrieving information about pooled normals", e);
        }

        return pooledNormals;
    }

    private static List<DataRecord> getPotentialPooledNormalQCs(User apiUser, DataRecordManager dataRecordManager)
            throws Exception {
        String query = String.format("%s LIKE '%s'", VeloxConstants.OTHER_SAMPLE_ID, "%POOLEDNORMAL%");

        DEV_LOGGER.info(String.format("Query used to look for pooled normals: %s", query));
        return dataRecordManager.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC,
                query, apiUser);
    }

    public Map<DataRecord, Collection<String>> getAllPooledNormals(KickoffRequest request, User user,
                                                                   DataRecordManager dataRecordManager) {
        Map<DataRecord, Collection<String>> pooledNormals = new LinkedHashMap<>(SampleInfoImpact.getPooledNormals());

        SetMultimap<DataRecord, String> pooledNormalsFromQc = populatePoolNormals(user, dataRecordManager,
                request);

        pooledNormals.putAll(pooledNormalsFromQc.asMap());
        return pooledNormals;
    }
}
