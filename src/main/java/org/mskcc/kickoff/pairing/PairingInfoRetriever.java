package org.mskcc.kickoff.pairing;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

@Component
public class PairingInfoRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final BiPredicate<Sample, Sample> pairingInfoValidPredicate;
    private final ObserverManager observerManager;

    public PairingInfoRetriever(@Qualifier("pairingInfoValidPredicate") BiPredicate<Sample, Sample>
                                        pairingInfoValidPredicate, ObserverManager observerManager) {
        this.pairingInfoValidPredicate = pairingInfoValidPredicate;
        this.observerManager = observerManager;
    }

    public Map<String, String> retrieve(KickoffRequest request) {
        List<Sample> tumors = request.getAllValidSamples().values().stream()
                .filter(Sample::isTumor)
                .collect(Collectors.toList());

        Map<String, String> pairings = new LinkedHashMap<>();
        for (Sample tumor : tumors) {
            if (tumor.isPooledNormal()) {
                String message = String.format("A sample with id: %s cannot be tumor sample", tumor.getCmoSampleId());
                Utils.setExitLater(true);
                PM_LOGGER.error(message);
                DEV_LOGGER.error(message);
                continue;
            }

            Sample pairingSample = tumor.getPairing();
            if (pairingSample == null)
                continue;

            String tumorIgoId = tumor.getIgoId();
            Optional<Sample> normal = Optional.empty();

            if (StringUtils.isEmpty(pairingSample.getIgoId()) && (StringUtils.isEmpty(pairingSample.getCmoSampleId())
                    || Objects.equals(pairingSample.getCmoSampleId(), org.mskcc.util.Constants.UNDEFINED))) {
                if (!Objects.equals(request.getRequestType(), Constants.EXOME))
                    continue;
            } else {
                if (!request.isSampleFromRequest(pairingSample.getIgoId())) {
                    String message = String.format("Normal: %s (%s) matching with tumor: %s (%s) does NOT belong to " +
                                    "request: %s. The normal will be changed to na.", pairingSample.getCmoSampleId(),
                            pairingSample.getIgoId(), tumor.getCmoSampleId(), tumorIgoId, request.getId());
                    PM_LOGGER.log(PmLogPriority.WARNING, message);
                    DEV_LOGGER.warn(message);
                    observerManager.notifyObserversOfError(ManifestFile.PAIRING, new GenerationError(message,
                            ErrorCode.UNMATCHED_TUMOR));
                } else {
                    normal = Optional.of(getNormal(request, pairingSample));
                }
            }

            if (pairings.keySet().contains(tumorIgoId)) {
                String message = String.format("Multiple pairing records for %s! This is not supposed to happen.",
                        tumorIgoId);
                PM_LOGGER.log(PmLogPriority.WARNING, message);
                DEV_LOGGER.warn(message);
                String message1 = String.format("Tumor is matched with two different normals. I have no idea how this" +
                        " happened! Tumor: %s Normal: %s", tumor.getCmoSampleId(), pairingSample.getIgoId());
                Utils.setExitLater(true);
                PM_LOGGER.error(message1);
                DEV_LOGGER.error(message1);
                continue;
            }

            String normalCmoId = Constants.NA_LOWER_CASE;
            if (normal.isPresent() && pairingInfoValidPredicate.test(tumor, normal.get()))
                normalCmoId = normal.get().getCorrectedCmoSampleId();

            pairings.put(tumor.getCorrectedCmoSampleId(), normalCmoId);
        }

        if (Objects.equals(request.getRequestType(), Constants.EXOME)) {
            Set<String> normals = new HashSet<>(pairings.values());
            if (normals.size() == 1 && normals.contains(Constants.NA_LOWER_CASE)) {
                return Collections.emptyMap();
            }
        }

        if (pairings.size() > 0) {
            Set<String> temp1 = new HashSet<>(tumors.stream()
                    .map(Sample::getCmoSampleId)
                    .collect(Collectors.toList()));

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

    private Sample getNormal(KickoffRequest request, Sample pairingSample) {
        return request.getSample(pairingSample.getIgoId());
    }

}
