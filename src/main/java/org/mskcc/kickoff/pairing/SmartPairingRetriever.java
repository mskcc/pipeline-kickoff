package org.mskcc.kickoff.pairing;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.domain.sample.Sample;
import org.mskcc.domain.sample.TumorNormalType;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.mskcc.domain.sample.Preservation.*;


@Component
public class SmartPairingRetriever {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final String DMP_ID_PATTERN = "(P-[0-9]{7})-([TN])([0-9])+-([A-Za-z0-9]+)";

    private final BiPredicate<Sample, Sample> pairingInfoValidPredicate;
    private Map<String, String> dmpToIgoAssay = new HashMap<>();
    private ReadOnlyExternalSamplesRepository externalSamplesRepository;
    private Map<String, Collection<KickoffExternalSample>> cmoPatientId2ExtSamples = new HashMap<>();

    @Autowired
    public SmartPairingRetriever(@Qualifier("pairingInfoValidPredicate") BiPredicate<Sample, Sample>
                                         pairingInfoValidPredicate,
                                 ReadOnlyExternalSamplesRepository externalSamplesRepository) {
        this.pairingInfoValidPredicate = pairingInfoValidPredicate;
        this.externalSamplesRepository = externalSamplesRepository;

        dmpToIgoAssay.put("IM3", Recipe.IMPACT_341.getValue());
        dmpToIgoAssay.put("IM5", Recipe.IMPACT_410.getValue());
        dmpToIgoAssay.put("IM6", Recipe.IMPACT_468.getValue());
        dmpToIgoAssay.put("IM7", "IMPACT505");
        dmpToIgoAssay.put("IH3", Recipe.HEME_PACT_V_3.getValue());
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

            String cmoPatientId = patient.getPatientId();
            DEV_LOGGER.info(String.format("Normal samples found for request %s: for patient: %s %s", request.getId(),
                    cmoPatientId, normalSamplesFromCurrentRequest));

            DEV_LOGGER.info(String.format("Normal samples found for project %s: for patient: %s %s", request
                            .getProjectId(),
                    cmoPatientId, normalSamplesFromCurrentProject));

            Set<Sample> tumorSamples = getTumorSamples(patient.getSamples());
            if (tumorSamples.size() == 0)
                DEV_LOGGER.warn(String.format("No tumor samples found for patient %s", cmoPatientId));

            for (Sample tumor : tumorSamples) {
                try {
                    String tumorCorrectedCmoId = tumor.getCorrectedCmoSampleId();
                    validateSampleId(tumor, tumorCorrectedCmoId);

                    String normalCorrectedCmoId = tryToPairNormal(request, pooledNormals,
                            normalSamplesFromCurrentRequest,
                            normalSamplesFromCurrentProject, tumor, cmoPatientId);

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

    private Collection<KickoffExternalSample> getDmpNormalsForPatient(String cmoPatientId) {
        try {
            if (!cmoPatientId2ExtSamples.containsKey(cmoPatientId)) {
                Collection<ExternalSample> externalSamples = externalSamplesRepository.getByPatientCmoId(cmoPatientId);

                List<KickoffExternalSample> normalExternalSamples = externalSamples.stream()
                        .filter(s -> Objects.equals(s.getTumorNormal(), TumorNormalType.NORMAL.getValue()))
                        .map(s -> KickoffExternalSample.convert(s))
                        .collect(Collectors.toList());

                cmoPatientId2ExtSamples.put(cmoPatientId, normalExternalSamples);

                return normalExternalSamples;
            }

            return cmoPatientId2ExtSamples.get(cmoPatientId);
        } catch (Exception e) {
            ManifestFile.PAIRING.addGenerationError(new GenerationError("Unable to retrieve External Samples: " + e
                    .getMessage(), ErrorCode.EXTERNAL_SAMPLES_RETRIEVAL_ERROR));
            throw e;
        }
    }

    private List<Sample> getNormalSamplesForProjectForPatient(KickoffRequest request, Patient patient) {
        String patientFieldKey = RequestDataPropagator.getPatientFieldKey(request);
        return request.getValidNormalsForProject().values()
                .stream()
                .filter(s -> Objects.equals(s.getCmoSampleInfo().getFields().getOrDefault(patientFieldKey, ""),
                        patient.getPatientId()))
                .filter(s -> s.getRecipe() == request.getRecipe())
                .collect(Collectors.toList());
    }

    private void validateSampleId(Sample sample, String sampleId) {
        if (StringUtils.isEmpty(sampleId))
            throw new RuntimeException(String.format("Cmo sample id is empty for sample: %s", sample.getIgoId()));
    }

    private Collection<Sample> getPooledNormals(KickoffRequest request) {
        return request.getAllValidSamples(Sample::isPooledNormal).values();
    }

    private String tryToPairNormal(KickoffRequest request,
                                   Collection<Sample> pooledNormals,
                                   Collection<Sample> normalSamplesFromCurrentRequest,
                                   Collection<Sample> normalSamplesFromCurrentProject,
                                   Sample tumor, String cmoPatientId) {
        String normalCorrectedCmoId = tryToPairNormalWithRequestNormals(normalSamplesFromCurrentRequest, tumor);

        if (!normalFound(normalCorrectedCmoId)) {
            DEV_LOGGER.info("Normal sample not found in request samples. Will look for in project samples");
            normalCorrectedCmoId = tryToPairNormalWithProjectNormals(normalSamplesFromCurrentProject, tumor);
            addNormalToPatientIfNeeded(request, normalCorrectedCmoId);
        }

        if (!normalFound(normalCorrectedCmoId)) {
            DEV_LOGGER.info("Normal sample not found in project samples. Will look for in DMP samples");
            Collection<KickoffExternalSample> dmpNormals = getDmpNormalsForPatient(cmoPatientId);
            normalCorrectedCmoId = tryToPairNormalWithDMP(dmpNormals, tumor, request);
        }

        if (!normalFound(normalCorrectedCmoId)) {
            pooledNormals = getPooledNormals(request);
            DEV_LOGGER.info(String.format("Pooled normal samples found for request %s: %s", request.getId(),
                    pooledNormals));

            DEV_LOGGER.info("Normal sample not found in DMP samples. Will look for in pooled normals");
            normalCorrectedCmoId = tryToPairPooledNormal(tumor, pooledNormals);
        }

        return normalCorrectedCmoId;
    }

    private String tryToPairNormalWithDMP(Collection<KickoffExternalSample> dmpNormals, Sample tumor, KickoffRequest
            request) {
        Optional<KickoffExternalSample> optional = dmpNormals.stream()
                .filter(s -> assayMatches(s, tumor.getRecipe().getValue()))
                .findFirst();

        if (optional.isPresent()) {
            KickoffExternalSample externalSample = optional.get();
            addToPatient(request, externalSample);

            request.addExternalSample(externalSample);

            return externalSample.getCorrectedCmoSampleId();
        }

        return Constants.NA_LOWER_CASE;
    }

    private void addToPatient(KickoffRequest request, KickoffExternalSample externalSample) {
        Patient patient = request.getPatients().get(externalSample.getPatientId());
        if (patient == null)
            patient = request.getPatients().get(externalSample.getCmoSampleInfo().getCmoPatientId());
        if (patient == null)
            patient = request.getPatients().get(externalSample.getCmoPatientId());

        if (patient == null)
            DEV_LOGGER.warn(String.format("Could not find patient with id %s (%s, %s) to add sample %s to it",
                    externalSample.getPatientId(), externalSample.getCmoPatientId(), externalSample.getCmoSampleInfo
                            ().getCmoPatientId(),
                    externalSample));
        else {
            patient.addSample(externalSample);
            DEV_LOGGER.debug(String.format("Normal %s added to patient: %s", externalSample, patient.getPatientId()));
        }
    }

    private boolean assayMatches(KickoffExternalSample externalSample, String tumorRecipe) {
        Pattern pattern = Pattern.compile(DMP_ID_PATTERN);
        Matcher matcher = pattern.matcher(externalSample.getExternalId());

        if (matcher.matches()) {
            String assay = matcher.group(4);
            if (dmpToIgoAssay.containsKey(assay)) {
                String igoAssay = dmpToIgoAssay.get(assay);
                return Objects.equals(tumorRecipe, igoAssay);
            }
        }

        return false;
    }

    private String tryToPairNormalWithProjectNormals(Collection<Sample> normalSamplesFromCurrentProject, Sample tumor) {
        return getNormal(normalSamplesFromCurrentProject, tumor);
    }

    private String tryToPairNormalWithRequestNormals(Collection<Sample> normalSamplesFromCurrentRequest, Sample tumor) {
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
        Collection<Sample> assayCompatibleNormals = getAssayCompatibleNormals(tumor, normalSamples);

        if (assayCompatibleNormals.size() == 0) {
            String message = String.format("There is no normal sample compatible with tumor's %s recipe %s. Normals " +
                    "found: %s.", tumor.getIgoId(), tumor
                    .getRecipe(), getSampleIdsWithRecipes(normalSamples));
            DEV_LOGGER.warn(message);

            return Constants.NA_LOWER_CASE;
        }

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

    private String getSampleIdsWithRecipes(Collection<Sample> samples) {
        return samples.stream()
                .map(s -> String.format("%s - %s", s.getIgoId(), s.getRecipe()))
                .collect(Collectors.joining(","));
    }

    private Collection<Sample> getAssayCompatibleNormals(Sample tumor, Collection<Sample> normalSamples) {
        return normalSamples.stream()
                .filter(s -> s.getRecipe() == tumor.getRecipe())
                .collect(Collectors.toList());
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
