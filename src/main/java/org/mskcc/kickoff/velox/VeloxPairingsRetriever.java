package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.user.User;
import org.apache.commons.lang3.StringUtils;
import org.mskcc.kickoff.domain.PairingInfo;
import org.mskcc.util.VeloxConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class VeloxPairingsRetriever {
    private final User user;

    public VeloxPairingsRetriever(User user) {
        this.user = user;
    }

    public List<PairingInfo> retrieve(DataRecord dataRecord) {
        try {
            List<DataRecord> pairingRecords = Arrays.asList(dataRecord.getChildrenOfType(VeloxConstants.PAIRING_INFO, user));
            List<PairingInfo> pairings = new ArrayList<>();
            for (DataRecord pairingRecord : pairingRecords) {
                try {
                    String tumorId = pairingRecord.getStringVal(VeloxConstants.TUMOR_ID, user);
                    String normalId = pairingRecord.getStringVal(VeloxConstants.NORMAL_ID, user);

                    if (StringUtils.isEmpty(tumorId) || StringUtils.isEmpty(normalId))
                        continue;

                    pairings.add(new PairingInfo(tumorId, normalId));
                } catch (Exception e) {
                    throw new RuntimeException(String.format("Unable to retrieve pairing with Record id: %s ", pairingRecord.getRecordId()));
                }
            }

            return pairings;
        } catch (Exception e) {
            throw new VeloxPairingsRetriever.PairingInfoRetrievalException(e);
        }
    }

    private class PairingInfoRetrievalException extends RuntimeException {
        public PairingInfoRetrievalException(Exception e) {
            super(e);
        }
    }
}
