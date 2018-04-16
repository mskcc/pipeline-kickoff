package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.util.*;

public class PairingInfoRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public Map<String, String> retrieve(Map<String, String> tumorIgoToCmoId, KickoffRequest request) {
        Map<String, String> pairings = new LinkedHashMap<>();
        for (Sample sample : getValidTumorSamples(request)) {
            if (sample.getCmoSampleId().startsWith("CTRL-")) {
                String message = String.format("A sample with id: %s cannot be tumor sample", sample.getCmoSampleId());
                Utils.setExitLater(true);
                PM_LOGGER.error(message);
                DEV_LOGGER.error(message);
                continue;
            }

            Sample pairingSample = sample.getPairing();
            if (pairingSample == null)
                continue;

            String tumorIgoId = sample.getIgoId();
            String normalCmoId = getInitialNormalId(pairingSample);

            if (StringUtils.isEmpty(pairingSample.getIgoId()) && (StringUtils.isEmpty(pairingSample.getCmoSampleId())
                    || Objects.equals(pairingSample.getCmoSampleId(), org.mskcc.util.Constants.UNDEFINED))) {
                if (Objects.equals(request.getRequestType(), Constants.EXOME))
                    normalCmoId = Constants.NA_LOWER_CASE;
                else continue;
            } else {
                if (!request.isSampleFromRequest(pairingSample.getIgoId())) {
                    String message = String.format("Normal: %s (%s) matching with tumor: %s (%s) does NOT belong to " +
                                    "request: %s. The normal will be changed to na.", pairingSample.getCmoSampleId(),
                            pairingSample.getIgoId(),
                            tumorIgoToCmoId.get(tumorIgoId), tumorIgoId, request.getId());
                    PM_LOGGER.log(PmLogPriority.WARNING, message);
                    DEV_LOGGER.warn(message);
                    normalCmoId = Constants.NA_LOWER_CASE;
                } else {
                    normalCmoId = getNormalCmoId(request, pairingSample);
                }
            }

            if (pairings.keySet().contains(tumorIgoId)) {
                String message = String.format("Multiple pairing records for %s! This is not supposed to happen.",
                        tumorIgoId);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
                String message1 = String.format("Tumor is matched with two different normals. I have no idea how this" +
                        " happened! Tumor: %s Normal: %s", sample.getCmoSampleId(), normalCmoId);
                Utils.setExitLater(true);
                PM_LOGGER.error(message1);
                DEV_LOGGER.error(message1);
                continue;
            }

            pairings.put(sample.getCorrectedCmoSampleId(), normalCmoId);
        }

        if (Objects.equals(request.getRequestType(), Constants.EXOME)) {
            Set<String> normals = new HashSet<>(pairings.values());
            if (normals.size() == 1 && normals.contains(Constants.NA_LOWER_CASE)) {
                return Collections.emptyMap();
            }
        }

        if (pairings.size() > 0) {
            Set<String> temp1 = new HashSet<>(tumorIgoToCmoId.values());
            Set<String> temp2 = new HashSet<>(pairings.keySet());
            temp1.removeAll(temp2);
            if (temp1.size() > 0) {
                String message = String.format("one or more pairing records was not found! %s", Arrays.toString
                        (temp1.toArray()));
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
            }
        }

        return pairings;
    }

    private Collection<Sample> getValidTumorSamples(KickoffRequest request) {
        Set<Sample> validTumorSamples = new HashSet<>(request.getAllValidSamples(s -> s.isTumor()).values());
        validTumorSamples.addAll(request.getTumorExternalSamples());

        return validTumorSamples;
    }

    private String getInitialNormalId(Sample pairingSample) {
        if (!Objects.equals(pairingSample.getCmoSampleId(), org.mskcc.util.Constants.UNDEFINED))
            return pairingSample.getCmoSampleId();
        if (!StringUtils.isEmpty(pairingSample.getIgoId()))
            return pairingSample.getIgoId();
        return Constants.NA_LOWER_CASE;
    }

    private String getNormalCmoId(KickoffRequest request, Sample pairingSample) {
        return request.getSample(pairingSample.getIgoId()).get(Constants.CORRECTED_CMO_ID);
    }
}
