package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;

import java.util.LinkedHashMap;
import java.util.Map;

public class PairingsResolver {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final PairingInfoRetriever pairingInfoRetriever;
    private final SmartPairingRetriever smartPairingRetriever;

    public PairingsResolver(PairingInfoRetriever pairingInfoRetriever, SmartPairingRetriever smartPairingRetriever) {
        this.pairingInfoRetriever = pairingInfoRetriever;
        this.smartPairingRetriever = smartPairingRetriever;
    }

    public Map<String, String> resolve(KickoffRequest request) {
        Map<String, String> tumorIgoToCmoId = new LinkedHashMap<>();
        Map<String, String> normalIgoToCmoId = new LinkedHashMap<>();

        for (Sample sample : request.getAllValidSamples().values()) {
            if (sample.isTumor()) {
                tumorIgoToCmoId.put(sample.get(Constants.IGO_ID), sample.get(Constants.CORRECTED_CMO_ID));
            } else {
                normalIgoToCmoId.put(sample.get(Constants.IGO_ID), sample.get(Constants.CORRECTED_CMO_ID));
            }
        }

        Map<String, String> pairings = smartPairingRetriever.retrieve(request);
        Map<String, String> pairingInfos = pairingInfoRetriever.retrieve(tumorIgoToCmoId, request);

        for (Map.Entry<String, String> pairingInfo : pairingInfos.entrySet()) {
            if (shouldOverride(pairingInfo)) {
                DEV_LOGGER.info(String.format("Overriding pairing info for tumor: %s, old normal: %s, new normal: " +
                        "%s", pairingInfo.getKey(), pairings.get(pairingInfo.getKey()), pairingInfo.getValue()));
                pairings.put(pairingInfo.getKey(), pairingInfo.getValue());
            }
        }

        return pairings;
    }

    private boolean shouldOverride(Map.Entry<String, String> pairingInfo) {
        return !isEmptyPairingInfo(pairingInfo);
    }

    private boolean isEmptyPairingInfo(Map.Entry<String, String> pairingInfo) {
        return StringUtils.isEmpty(pairingInfo.getKey()) || StringUtils.isEmpty(pairingInfo.getValue());
    }
}
