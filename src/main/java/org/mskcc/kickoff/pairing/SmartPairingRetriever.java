package org.mskcc.kickoff.pairing;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.mskcc.domain.sample.Preservation.*;


@Component
public class SmartPairingRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final BiPredicate<Sample, Sample> pairingInfoValidPredicate;

    @Autowired
    public SmartPairingRetriever(@Qualifier("pairingInfoValidPredicate") BiPredicate<Sample, Sample>
                                         pairingInfoValidPredicate) {
        this.pairingInfoValidPredicate = pairingInfoValidPredicate;
    }

    public Map<String, String> retrieve(KickoffRequest request) {
        if (request.getPatients().isEmpty()) {
            String noPatientsMessage = String.format("There are no patients for request: %s. There will be no pairing" +
                    " file generated", request.getId());
            PM_LOGGER.log(PmLogPriority.WARNING, noPatientsMessage);
            DEV_LOGGER.warn(noPatientsMessage);
            return Collections.emptyMap();
        }

        return getSmartPairings(request);
    }

    private Map<String, String> getSmartPairings(KickoffRequest request) {
        Collection<Sample> pooledNormals = getPooledNormals(request);
        DEV_LOGGER.info(String.format("Pooled normal samples found for request %s: %s", request.getId(),
                pooledNormals));

        Map<String, String> smartPairings = new LinkedHashMap<>();
        for (Patient patient : request.getPatients().values()) {
            Set<Sample> normalSamplesFromCurrentRequest = getNormalSamples(patient.getSamples());
            Collection<Sample> normalSamplesFromCurrentProject = getNormalSamplesForProjectForPatient(request, patient);

            DEV_LOGGER.info(String.format("Normal samples found for request %s: for patient: %s %s", request.getId(),
                    patient.getPatientId(), normalSamplesFromCurrentRequest));

            DEV_LOGGER.info(String.format("Normal samples found for project %s: for patient: %s %s", request
                            .getProjectId(),
                    patient.getPatientId(), normalSamplesFromCurrentProject));

            for (Sample tumor : getTumorSamples(patient.getSamples())) {
                try {
                    String tumorCorrectedCmoId = tumor.getCorrectedCmoSampleId();
                    validateSampleId(tumor, tumorCorrectedCmoId);

                    String normalCorrectedCmoId = tryToPairNormal(request, pooledNormals,
                            normalSamplesFromCurrentRequest,
                            normalSamplesFromCurrentProject, tumor);

                    DEV_LOGGER.info(String.format("Smart pairing found: tumor: %s - normal: %s", tumorCorrectedCmoId,
                            normalCorrectedCmoId));

                    smartPairings.put(tumorCorrectedCmoId, normalCorrectedCmoId);
                } catch (Exception e) {
                    DEV_LOGGER.warn(String.format("Error while trying to pair normal for tumor: %s", tumor.getIgoId()
                    ), e);
                }
            }
        }

        return smartPairings;
    }

    private List<Sample> getNormalSamplesForProjectForPatient(KickoffRequest request, Patient patient) {
        String patientFieldKey = RequestDataPropagator.getPatientFieldKey(request);
        return request.getValidNormalsForProject().values()
                .stream()
                .filter(s -> Objects.equals(s.getCmoSampleInfo().getFields().getOrDefault(patientFieldKey, ""),
                        patient.getPatientId()))
                .collect(Collectors.toList());
    }

    private void validateSampleId(Sample sample, String sampleId) {
        if (StringUtils.isEmpty(sampleId))
            throw new RuntimeException(String.format("Cmo sample id is empty for sample: %s", sample.getIgoId()));
    }

    private Collection<Sample> getPooledNormals(KickoffRequest request) {
        return request.getAllValidSamples(Sample::isPooledNormal).values();
    }

    private String tryToPairNormal(KickoffRequest request, Collection<Sample> pooledNormals, Collection<Sample>
            normalSamplesFromCurrentRequest, Collection<Sample> normalSamplesFromCurrentProject, Sample tumor) {
        String normalCorrectedCmoId = tryToPairNormalWithReqestNormals(normalSamplesFromCurrentRequest, tumor);

        if (!normalFound(normalCorrectedCmoId)) {
            DEV_LOGGER.info("Normal sample not found in request samples. Will look for in project samples");
            normalCorrectedCmoId = tryToPairNormalWithProjectNormals(normalSamplesFromCurrentProject, tumor);
            addNormalToPatientIfNeeded(request, normalCorrectedCmoId);
        }

        if (!normalFound(normalCorrectedCmoId)) {
            DEV_LOGGER.info("Normal sample not found in project samples. Will look for in pooled normals");
            normalCorrectedCmoId = tryToPairPooledNormal(tumor, pooledNormals);
        }

        return normalCorrectedCmoId;
    }

    private String tryToPairNormalWithProjectNormals(Collection<Sample> normalSamplesFromCurrentProject, Sample tumor) {
        return getNormal(normalSamplesFromCurrentProject, tumor);
    }

    private String tryToPairNormalWithReqestNormals(Collection<Sample> normalSamplesFromCurrentRequest, Sample tumor) {
        return getNormal(normalSamplesFromCurrentRequest, tumor);
    }

    private boolean normalFound(String normalCorrectedCmoId) {
        return !Objects.equals(normalCorrectedCmoId, Constants.NA_LOWER_CASE);
    }

    private void addNormalToPatientIfNeeded(KickoffRequest request, String normalCorrectedCmoId) {
        if (normalFound(normalCorrectedCmoId)) {
            String normCorrCmoId = normalCorrectedCmoId;
            Sample normal = request.getValidNormalsForProject().values().stream()
                    .filter(s -> Objects.equals(s.getCorrectedCmoSampleId(), normCorrCmoId))
                    .findFirst().get();
            Patient patient = request.getPatients().get(normal.getPatientId());
            if (patient == null)
                patient = request.getPatients().get(normal.getCmoSampleInfo().getCmoPatientId());
            if (patient == null)
                patient = request.getPatients().get(normal.getCmoPatientId());

            request.addUsedNormalFromProject(normal);

            if (patient == null)
                DEV_LOGGER.warn(String.format("Could not find patient with id %s (%s, %s) to add sample %s to it",
                        normal.getPatientId(), normal.getCmoPatientId(), normal.getCmoSampleInfo().getCmoPatientId(),
                        normal));
            else {
                patient.addSample(normal);
                DEV_LOGGER.debug(String.format("Normal %s added to patient: %s", normal, patient.getPatientId()));
            }
        }
    }

    private String tryToPairPooledNormal(Sample tumor, Collection<Sample> pooledNormals) {
        DEV_LOGGER.info(String.format("Trying to pair one of Pooled normals %s to tumor: %s", pooledNormals, tumor
                .getIgoId()));
        List<Sample> seqTypeCompatiblePooledNormals = getSeqTypeCompatibleNormals(tumor, pooledNormals);

        if (seqTypeCompatiblePooledNormals.isEmpty()) {
            String message = String.format("There is no pooled normal sample compatible with tumor's %s sequencer " +
                    "types: %s. Pooled Normals found: %s.", tumor.getIgoId(), tumor
                    .getInstrumentTypes(), getSampleIdsWithSeqTypes(pooledNormals));
            DEV_LOGGER.warn(message);

            return Constants.NA_LOWER_CASE;
        }

        if (isFfpe(tumor))
            return tryToMatchFfpePooledNormal(seqTypeCompatiblePooledNormals);

        return tryToMatchFrozenPooledNormal(seqTypeCompatiblePooledNormals);
    }

    private boolean isFfpe(Sample sample) {
        String preservation = sample.get(Constants.SPECIMEN_PRESERVATION_TYPE);
        DEV_LOGGER.info(String.format("Sample %s has preservation type %s", sample.getIgoId(), preservation));
        return fromString(preservation) == FFPE;
    }

    private String tryToMatchFrozenPooledNormal(Collection<Sample> pooledNormals) {
        DEV_LOGGER.info(String.format("Trying to find Frozen Pooled Normal out of: %s", pooledNormals));

        Optional<Sample> frozenPooledNormal = pooledNormals.stream()
                .filter(s -> isFrozen(s))
                .findFirst();

        if (frozenPooledNormal.isPresent() && StringUtils.isEmpty(frozenPooledNormal.get().getCorrectedCmoSampleId()))
            throw new RuntimeException(String.format("Cmo Sample id is empty for sample: %s", frozenPooledNormal.get
                    ().getIgoId()));

        return frozenPooledNormal.map(Sample::getCorrectedCmoSampleId).orElse(Constants.NA_LOWER_CASE);
    }

    private boolean isFrozen(Sample s) {
        String preservation = s.get(Constants.SPECIMEN_PRESERVATION_TYPE);
        DEV_LOGGER.info(String.format("Sample %s has preservation type %s", s.getIgoId(), preservation));
        return fromString(preservation) == FROZEN;
    }

    private String tryToMatchFfpePooledNormal(Collection<Sample> pooledNormals) {
        DEV_LOGGER.info(String.format("Trying to find FFPE Pooled Normal out of: %s", pooledNormals));
        Optional<Sample> ffpePooledNormal = pooledNormals.stream()
                .filter(s -> isFfpe(s))
                .findFirst();
        if (ffpePooledNormal.isPresent() && StringUtils.isEmpty(ffpePooledNormal.get().getCorrectedCmoSampleId()))
            throw new RuntimeException(String.format("Cmo Sample id is empty for sample: %s", ffpePooledNormal.get()
                    .getIgoId()));

        return ffpePooledNormal.map(Sample::getCorrectedCmoSampleId).orElse(Constants.NA_LOWER_CASE);
    }

    private String getNormal(Collection<Sample> normalSamples, Sample tumor) {
        List<Sample> seqTypeCompatibleNormals = getSeqTypeCompatibleNormals(tumor, normalSamples);

        if (seqTypeCompatibleNormals.size() == 0) {
            String message = String.format("There is no normal sample compatible with tumor's %s sequencer " +
                    "types: %s. Normals found: %s.", tumor.getIgoId(), tumor
                    .getInstrumentTypes(), getSampleIdsWithSeqTypes(normalSamples));
            DEV_LOGGER.warn(message);

            return Constants.NA_LOWER_CASE;
        }

        String tumorPreservation = tumor.get(Constants.SPECIMEN_PRESERVATION_TYPE);
        List<Sample> preservationCompatibleNormals = getPreservationCompatibleNormals(tumorPreservation,
                seqTypeCompatibleNormals);

        if (preservationCompatibleNormals.size() > 0)
            seqTypeCompatibleNormals = new ArrayList<>(preservationCompatibleNormals);

        String tumorTissueSite = tumor.get(Constants.TISSUE_SITE);
        List<Sample> tissueSiteCompatibleNormals = getTissueSiteCompatibleNormals(tumorTissueSite,
                seqTypeCompatibleNormals);

        Sample pairedNormal;
        if (tissueSiteCompatibleNormals.size() > 0) {
            pairedNormal = tissueSiteCompatibleNormals.get(0);
        } else {
            pairedNormal = seqTypeCompatibleNormals.get(0);
        }

        return pairedNormal.getCorrectedCmoSampleId();
    }

    private String getSampleIdsWithSeqTypes(Collection<Sample> samples) {
        return samples.stream()
                .map(s -> String.format("%s - %s", s.getIgoId(), s.getInstrumentTypes()))
                .collect(Collectors.joining(","));
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

    private List<Sample> getSeqTypeCompatibleNormals(Sample tumor, Collection<Sample> normalSamples) {
        return normalSamples.stream()
                .filter(n -> pairingInfoValidPredicate.test(tumor, n))
                .collect(Collectors.toList());
    }

    private Set<Sample> getNormalSamples(Set<Sample> samples) {
        return samples.stream()
                .filter(s -> !s.isTumor() && !s.isPooledNormal())
                .collect(Collectors.toSet());
    }

    private Set<Sample> getTumorSamples(Set<Sample> samples) {
        return samples.stream()
                .filter(s -> s.isTumor())
                .collect(Collectors.toSet());
    }
}
