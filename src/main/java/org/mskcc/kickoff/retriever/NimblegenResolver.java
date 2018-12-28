package org.mskcc.kickoff.retriever;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.util.Constants;
import org.mskcc.util.VeloxConstants;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class NimblegenResolver {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);

    public DataRecord resolve(DataRecordManager drm, DataRecord rec, User apiUser, boolean isPoolNormal) {
        // This should set Lib Yield, Capt Input, Capt Name, Bait Set, Spike ins
        List<DataRecord> nimbProtocols = new ArrayList<>();
        List<Object> validities = new ArrayList<>();
        List<Object> poolNames = new ArrayList<>();
        List<Object> igoIds = new ArrayList<>();

        String igoId = getIgoId(rec, apiUser);

        try {
            nimbProtocols = rec.getDescendantsOfType("NimbleGenHybProtocol", apiUser);
            validities = drm.getValueList(nimbProtocols, "Valid", apiUser);
            poolNames = drm.getValueList(nimbProtocols, "Protocol2Sample", apiUser);
            igoIds = drm.getValueList(nimbProtocols, VeloxConstants.SAMPLE_ID, apiUser);
        } catch (Exception e) {
            DEV_LOGGER.error(e);
        }

        DataRecord chosenRec = null;
        if (nimbProtocols == null || nimbProtocols.size() == 0) {
            throw new NoNimblegenHybProtocolFound();
        }
        // only one sample (rec) that was checked for
        // doesn't break out of this because I want to pick the LAST valid one.

        // First check for valid, containing a child sample pool name, and THIS.IGO_ID is found in SampleId
        Set<String> poolNameList = new HashSet<>();
        for (int i = 0; i < nimbProtocols.size(); i++) {
            boolean isValid = getIsValid(validities, i);
            String igoIdGiven = (String) igoIds.get(i);

            if (!igoIdGiven.contains(igoId)) {
                logWarning(String.format("Nimblegen D.R. has a different igo id than this sample: Nimb: %s, this " +
                        "sample: %s", igoIdGiven, igoId));
                continue;
            }

            String poolName = (String) poolNames.get(i);

            if (isValid && !poolName.isEmpty() && !poolName.equals(Constants.NULL)) {
                if (isPoolNormal) {
                    if (poolNameList.contains(poolName)) {
                        chosenRec = nimbProtocols.get(i);
                        break;
                    }
                } else {
                    chosenRec = nimbProtocols.get(i);
                    poolNameList.add(poolName);
                }
            }
        }

        if (chosenRec == null)
            chosenRec = getLastValidRecord(nimbProtocols, validities);

        return chosenRec;
    }

    private DataRecord getLastValidRecord(List<DataRecord> nimbProtocols, List<Object> validities) {
        for (int i = 0; i < nimbProtocols.size(); i++) {
            if (getIsValid(validities, i))
                return nimbProtocols.get(i);
        }

        throw new NoValidNimblegenHybrFound();
    }

    private String getIgoId(DataRecord rec, User apiUser) {
        try {
            return rec.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Cannot retrieve SampleId field for record %d", rec.getRecordId
                    ()));
        }
    }

    private boolean getIsValid(List<Object> validities, int i) {
        boolean isValid;

        try {
            isValid = (boolean) validities.get(i);
        } catch (NullPointerException e) {
            isValid = false;
        }

        return isValid;
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }

    public class NoValidNimblegenHybrFound extends RuntimeException {
    }

    public class NoNimblegenHybProtocolFound extends RuntimeException {
    }
}
