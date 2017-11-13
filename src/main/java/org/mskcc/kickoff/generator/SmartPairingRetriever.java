package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.PairingInfoValidPredicate;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class SmartPairingRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final BiPredicate<Sample, Sample> pairingInfoValidPredicate = new PairingInfoValidPredicate();

    public Map<String, String> retrieve(KickoffRequest request) {
        if (request.getPatients().isEmpty()) {
            String noPatientsMessage = String.format("There are no patients for request: %s. There will be no pairing" +
                    " file generated", request.getId());
            PM_LOGGER.log(PmLogPriority.WARNING, noPatientsMessage);
            DEV_LOGGER.warn(noPatientsMessage);
            return Collections.emptyMap();
        }

        Map<String, String> smartPairings = new LinkedHashMap<>();
        for (Patient patient : request.getPatients().values()) {
            List<Sample> tumorSamples = getTumorSamples(patient.getSamples());
            List<Sample> normalSamples = getNormalSamples(patient.getSamples());

            for (Sample tumor : tumorSamples) {
                String tumorCorrectedId = tumor.getProperties().get(Constants.CORRECTED_CMO_ID);
                String normalId = getNormal(normalSamples, tumor);
                DEV_LOGGER.info(String.format("Smart pairing found: tumor: %s - normal: %s", tumorCorrectedId,
                        normalId));
                smartPairings.put(tumorCorrectedId, normalId);
            }
        }

        return smartPairings;
    }

    private String getNormal(List<Sample> normalSamples, Sample tumor) {
        String tumorPreservation = tumor.get(Constants.SPECIMEN_PRESERVATION_TYPE);
        String tumorTissueSite = tumor.get(Constants.TISSUE_SITE);
        List<Sample> normals = getSeqTypeCompatibleNormals(tumor, normalSamples);

        if (normals.size() == 0)
            return Constants.NA_LOWER_CASE;

        List<Sample> preservationCompatibleNormals = getPreservationCompatibleNormals(tumorPreservation, normals);

        if (preservationCompatibleNormals.size() > 0)
            normals = new ArrayList<>(preservationCompatibleNormals);

        List<Sample> tissueSiteCompatibleNormals = getTissueSiteCompatibleNormals(tumorTissueSite, normals);

        Sample pairedNormal;
        if (tissueSiteCompatibleNormals.size() > 0) {
            pairedNormal = tissueSiteCompatibleNormals.get(0);
        } else {
            pairedNormal = normals.get(0);
        }

        return pairedNormal.get(Constants.CORRECTED_CMO_ID);
    }

    private List<Sample> getTissueSiteCompatibleNormals(String tumorTissueSite, List<Sample> normals) {
        return normals.stream()
                .filter(n -> n.get(Constants.TISSUE_SITE).equals(tumorTissueSite))
                .collect(Collectors.toList());
    }

    private List<Sample> getPreservationCompatibleNormals(String tumorPreservation, List<Sample> normals) {
        return normals.stream()
                .filter(n -> Objects.equals(n.get(Constants.SPECIMEN_PRESERVATION_TYPE), tumorPreservation))
                .collect(Collectors.toList());
    }

    private List<Sample> getSeqTypeCompatibleNormals(Sample tumor, List<Sample> normalSamples) {
        return normalSamples.stream()
                .filter(n -> pairingInfoValidPredicate.test(tumor, n))
                .collect(Collectors.toList());
    }

    private List<Sample> getNormalSamples(Set<Sample> samples) {
        return samples.stream()
                .filter(s -> !StringUtils.isEmpty(s.get(Constants.SAMPLE_CLASS)) && s.get(Constants.SAMPLE_CLASS)
                        .contains(Constants.NORMAL))
                .collect(Collectors.toList());
    }

    private List<Sample> getTumorSamples(Set<Sample> samples) {
        return samples.stream()
                .filter(s -> !StringUtils.isEmpty(s.get(Constants.SAMPLE_CLASS)) && !s.get(Constants.SAMPLE_CLASS)
                        .contains(Constants.NORMAL))
                .collect(Collectors.toList());
    }
}
