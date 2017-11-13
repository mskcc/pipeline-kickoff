package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.util.VeloxConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class VeloxPairingsRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final User user;

    public VeloxPairingsRetriever(User user) {
        this.user = user;
    }

    public List<PairingInfo> retrieve(DataRecord dataRecord, KickoffRequest kickoffRequest) {
        try {
            List<DataRecord> pairingRecords = Arrays.asList(dataRecord.getChildrenOfType(VeloxConstants.PAIRING_INFO,
                    user));
            List<PairingInfo> pairings = new ArrayList<>();
            for (DataRecord pairingRecord : pairingRecords) {
                try {
                    String tumorId = pairingRecord.getStringVal(VeloxConstants.TUMOR_ID, user);
                    String normalId = pairingRecord.getStringVal(VeloxConstants.NORMAL_ID, user);

                    if (StringUtils.isEmpty(tumorId) || StringUtils.isEmpty(normalId))
                        continue;

                    Sample tumor = getSample(kickoffRequest, tumorId);
                    Sample normal = getSample(kickoffRequest, normalId);

                    pairings.add(new PairingInfo(tumor, normal));
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Unable to retrieve pairing with Record id: %s ",
                            pairingRecord.getRecordId()));
                }
            }

            return pairings;
        } catch (Exception e) {
            throw new VeloxPairingsRetriever.PairingInfoRetrievalException(e);
        }
    }

    private Sample getSample(KickoffRequest kickoffRequest, String sampleId) {
        if (!kickoffRequest.getSamples().containsKey(sampleId)) {
            DEV_LOGGER.warn(String.format("Sample: %s from pairing info is not part of given request: %s", sampleId,
                    kickoffRequest.getId()));
            return Sample.getNotAvailableSample();
        }

        return kickoffRequest.getSample(sampleId);
    }

    private class PairingInfoRetrievalException extends RuntimeException {
        public PairingInfoRetrievalException(Exception e) {
            super(e);
        }
    }
}
