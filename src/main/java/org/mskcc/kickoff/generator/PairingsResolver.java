package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class PairingsResolver {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final PairingInfoRetriever pairingInfoRetriever;
    private final SmartPairingRetriever smartPairingRetriever;

    public PairingsResolver(PairingInfoRetriever pairingInfoRetriever, SmartPairingRetriever smartPairingRetriever) {
        this.pairingInfoRetriever = pairingInfoRetriever;
        this.smartPairingRetriever = smartPairingRetriever;
    }

    public Map<String, String> resolve(Request request) {
        LinkedHashMap<String, String> tumorIgoToCmoId = new LinkedHashMap<>();
        LinkedHashMap<String, String> normalIgoToCmoId = new LinkedHashMap<>();

        for (Sample sample : request.getAllValidSamples().values()) {
            if (!sample.get("SAMPLE_CLASS").contains("Normal")) {
                tumorIgoToCmoId.put(sample.get("IGO_ID"), sample.get("CORRECTED_CMO_ID"));
            } else {
                normalIgoToCmoId.put(sample.get("IGO_ID"), sample.get("CORRECTED_CMO_ID"));
            }
        }

        Map<String, String> pairings = smartPairingRetriever.retrieve(request);
        Map<String, String> pairingInfos = pairingInfoRetriever.retrieve(tumorIgoToCmoId, request);

        for (Map.Entry<String, String> pairingInfo : pairingInfos.entrySet()) {
            if(shouldOverride(pairings, pairingInfo)) {
                DEV_LOGGER.info(String.format("Overriding pairing info for tumor: %s, old normal: %s, new normal: %s", pairingInfo.getKey(), pairings.get(pairingInfo.getKey()), pairingInfo.getValue()));
                pairings.put(pairingInfo.getKey(), pairingInfo.getValue());
            }
        }

        return pairings;
    }

    private boolean shouldOverride(Map<String, String> pairings, Map.Entry<String, String> pairingInfo) {
        return !isEmptyPairingInfo(pairingInfo) && isNormalDifferent(pairings, pairingInfo);
    }

    private boolean isNormalDifferent(Map<String, String> pairings, Map.Entry<String, String> pairingInfo) {
        return !Objects.equals(pairings.get(pairingInfo.getKey()), pairingInfo.getValue());
    }

    private boolean isEmptyPairingInfo(Map.Entry<String, String> pairingInfo) {
        return StringUtils.isEmpty(pairingInfo.getKey()) || StringUtils.isEmpty(pairingInfo.getValue());
    }
}
