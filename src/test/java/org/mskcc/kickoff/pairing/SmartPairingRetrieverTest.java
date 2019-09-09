package org.mskcc.kickoff.pairing;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.Patient;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.*;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.util.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mskcc.domain.instrument.InstrumentType.*;
import static org.mskcc.domain.sample.Preservation.*;

public class SmartPairingRetrieverTest {
    private static final String TISSUE_SITE_1 = "TissueSite1";
    private static final String TISSUE_SITE_2 = "TissueSite2";
    private static final String RECIPE_IMPACT468 = Recipe.IMPACT_468.getValue();
    private static final String RECIPE_IMPACT410 = Recipe.IMPACT_410.getValue();
    private static final String PATIEND_ID_1 = "pat1";
    private static final String PATIEND_ID_2 = "pat2";
    private static final String PATIEND_ID_3 = "pat3";
    private final ReadOnlyExternalSamplesRepository extSamplesRepoMock = mock
            (ReadOnlyExternalSamplesRepository.class);
    private BiPredicate<Sample, Sample> pairingInfoValidPredicate = (s1, s2) -> Objects.equals(s1.getSeqNames(),
            s2.getSeqNames());
    private SmartPairingRetriever smartPairingRetriever = new SmartPairingRetriever(pairingInfoValidPredicate,
            extSamplesRepoMock);
    private KickoffRequest request;
    private int id = 0;

    @Before
    public void setUp() throws Exception {
        initSeqTypes();
        request = new KickoffRequest("id", new NormalProcessingType(mock(ProjectFilesArchiver.class)));
    }

    private void initSeqTypes() {
        mapNameToType(HISEQ.getValue(), HISEQ);
        mapNameToType(MISEQ.getValue(), MISEQ);
        mapNameToType(NOVASEQ.getValue(), NOVASEQ);
    }

    @Test
    public void whenThereAreNoPatients_shouldReturnEmptyList() throws Exception {
        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        assertThat(smartPairings).isEmpty();
    }

    @Test
    public void whenOnePatientWithOneTumorOneNormal_shouldReturnOnePairing() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String normalCorrectedCmoId = addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    private String addNormalSample(Patient patient, InstrumentType instrumentType, Preservation
            preservationType, String tissueSite, String recipe) {
        String id = getId();
        patient.addSample(getNormalSample(instrumentType, preservationType, tissueSite, id, recipe));

        return id;
    }

    @Test
    public void whenTumorAndNormalSamePreservationTissueSiteIsDifferent_shouldMatchByPreservation() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String normalCorrectedCmoId = addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenTumorAndNormalDifferentPreservationAndTissueSite_shouldMatchBySeqType() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String normalCorrectedCmoId = addNormalSample(patient, HISEQ, TRIZOL, TISSUE_SITE_2, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenTumorAndNormalDifferentSeqType_shouldNotCreatePairing() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        addNormalSample(patient, NOVASEQ, TRIZOL, TISSUE_SITE_2, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, Constants.NA_LOWER_CASE);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenOneNormalHasSameSeqTypeAndOtherHasDifferent_shouldMatchFirstOne() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String normalCorrectedCmoId1 = addNormalSample(patient, HISEQ, TRIZOL, TISSUE_SITE_2, RECIPE_IMPACT468);
        addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedCmoId1);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenOneNormalHasSameSeqAndSpecimenTypeAndOtherHasDifferentSpecimen_shouldMatchFirstOne() throws
            Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        addNormalSample(patient, NOVASEQ, TRIZOL, TISSUE_SITE_2, RECIPE_IMPACT468);
        String normalCorrectedId2 = addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedId2);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenOneNormalHasSameSeqPreservationNadTissueAndOtherHasOnlyDifferentTissue_shouldMatchFirstOne() throws
            Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);
        String normalCorrectedIdCmo1 = addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedIdCmo1);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    private String addTumorSample(Patient patient, InstrumentType instrumentType, Preservation
            preservationType, String tissueSite, String recipe) {
        String id = getId();
        patient.addSample(getTumorSample(instrumentType, preservationType, tissueSite, id, recipe));

        return id;
    }

    @Test
    public void whenOneFFPETumorAndFFPEPooledNormal_shouldReturnOnePairing() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pooledNormalCorrectedCmoId = addPooledNormal(HISEQ, FFPE);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, pooledNormalCorrectedCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenOneFFPETumorAndFrozenPooledNormal_shouldNotPairThen() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        addPooledNormal(HISEQ, FROZEN);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, Constants.NA_LOWER_CASE);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenNonFFPETumorAndFrozenPooledNormal_shouldPairThem() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, TRIZOL, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pooledNormalCmoId = addPooledNormal(HISEQ, FROZEN);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, pooledNormalCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenNonFFPETumorAndFFPEPooledNormal_shouldNotPairThem() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, TRIZOL, TISSUE_SITE_1, RECIPE_IMPACT468);
        addPooledNormal(HISEQ, FFPE);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, Constants.NA_LOWER_CASE);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }


    @Test
    public void whenMatchingPooledNormalAndNormal_shouldMatchWithNormal() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        addPooledNormal(HISEQ, FFPE);
        String normalCmoId = addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenMatchingPooledNormalAndDifferentSequencerNormal_shouldMatchWithPooledNormal() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pooledNormalCmoId = addPooledNormal(NOVASEQ, FFPE);
        addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, pooledNormalCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    @Test
    public void whenMultiplePatientsAndPooledNormals_shouldPairAllMatching() throws Exception {
        //given
        Patient patient1 = request.putPatientIfAbsent(PATIEND_ID_1);
        String pat1Tum1 = addTumorSample(patient1, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat1Tum2 = addTumorSample(patient1, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat1Tum3 = addTumorSample(patient1, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat1Tum4 = addTumorSample(patient1, MISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat1Tum5 = addTumorSample(patient1, MISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat1Tum6 = addTumorSample(patient1, MISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT410);

        String pat1Norm1 = addNormalSample(patient1, NOVASEQ, TRIZOL, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat1Norm2 = addNormalSample(patient1, HISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);
        String pat1Norm3 = addNormalSample(patient1, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        List<ExternalSample> extSamples = new ArrayList<>();
        extSamples = addToExternalSamples("P-1234567-N01-IM5", "C-345345-NW-d", extSamples);
        extSamples = addToExternalSamples("P-1234567-N02-IM5", "C-654654-NW-d", extSamples);

        when(extSamplesRepoMock.getByPatientCmoId(PATIEND_ID_1)).thenReturn(extSamples);

        Patient patient2 = request.putPatientIfAbsent(PATIEND_ID_2);
        String pat2Tum1 = addTumorSample(patient2, NOVASEQ, TRIZOL, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat2Tum2 = addTumorSample(patient2, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat2Tum3 = addTumorSample(patient2, HISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);
        String pat2Tum4 = addTumorSample(patient2, MISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);

        String pat2Norm1 = addNormalSample(patient2, NOVASEQ, TRIZOL, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat2Norm2 = addNormalSample(patient2, HISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);


        Patient patient3 = request.putPatientIfAbsent(PATIEND_ID_3);
        String pat3Tum1 = addTumorSample(patient3, NOVASEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat3Tum2 = addTumorSample(patient3, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat3Tum3 = addTumorSample(patient3, HISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);
        String pat3Tum4 = addTumorSample(patient3, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        String pat3Norm2 = addNormalSample(patient3, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);
        String pat3Norm3 = addNormalSample(patient3, HISEQ, FFPE, TISSUE_SITE_2, RECIPE_IMPACT468);
        String pat3Norm4 = addNormalSample(patient3, MISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        String pooledNormal1 = addPooledNormal(MISEQ, FFPE);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = ImmutableMap.<String, String>builder()
                .put(pat1Tum1, pat1Norm3)
                .put(pat1Tum2, pat1Norm2)
                .put(pat1Tum3, pat1Norm2)
                .put(pat1Tum4, pooledNormal1)
                .put(pat1Tum5, pooledNormal1)
                .put(pat1Tum6, "C-345345-NW-d")

                .put(pat2Tum1, pat2Norm1)
                .put(pat2Tum2, pat2Norm2)
                .put(pat2Tum3, pat2Norm2)
                .put(pat2Tum4, pooledNormal1)

                .put(pat3Tum1, Constants.NA_LOWER_CASE)
                .put(pat3Tum2, pat3Norm2)
                .put(pat3Tum3, pat3Norm3)
                .put(pat3Tum4, pat3Norm2)
                .build();

        assertThat(smartPairings.size()).isEqualTo(14);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    private List<ExternalSample> addToExternalSamples(String externalId, String cmoId, List<ExternalSample>
            extSamples) {
        ExternalSample externalSample = new ExternalSample(1, externalId, "P-1234567", "/abc/def/bla.bam", "DTH5432",
                SampleClass
                        .NORMAL.getValue(), SampleOrigin.WHOLE_BLOOD.getValue(), TumorNormalType.NORMAL.getValue());
        externalSample.setCmoId(cmoId);
        extSamples.add(externalSample);

        return extSamples;
    }

    @Test
    public void whenNoIgoSamplesAndOneMatchingDmpNormal_shouldPairWithMatchingDmpNormal() throws Exception {
        //given
        List<ExternalSample> pat1ExtSamples = new ArrayList<>();
        pat1ExtSamples.add(getExternalSample("P-1234567-N01-IM6", "C-345345-NW-d"));
        pat1ExtSamples.add(getExternalSample("P-1234567-N01-IM5", "C-345345-NW-d"));

        when(extSamplesRepoMock.getByPatientCmoId(PATIEND_ID_1)).thenReturn(pat1ExtSamples);

        List<ExternalSample> pat2ExtSamples = new ArrayList<>();
        pat1ExtSamples.add(getExternalSample("P-1234567-N01-IM6", "C-345345-NW-d"));
        pat1ExtSamples.add(getExternalSample("P-1234567-N01-IM5", "C-345345-NW-d"));

        when(extSamplesRepoMock.getByPatientCmoId(PATIEND_ID_2)).thenReturn(pat2ExtSamples);

        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1, RECIPE_IMPACT468);

        //when
        Map<String, String> pairings = smartPairingRetriever.retrieve(request);

        //then
        assertThat(pairings.size()).isEqualTo(1);
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, "C-345345-NW-d");

        assertThat(pairings).containsAllEntriesOf(expected);
    }

    private ExternalSample getExternalSample(String externalId, String cmoId) {
        ExternalSample matchingExtSample = new ExternalSample(1, externalId, "P-12345", "/abc/def/bla.bam", "DTH5432",
                SampleClass
                        .NORMAL.getValue(), SampleOrigin.WHOLE_BLOOD.getValue(), TumorNormalType.NORMAL.getValue());
        matchingExtSample.setCmoId(cmoId);
        return matchingExtSample;
    }

    private String addPooledNormal(InstrumentType instrumentType, Preservation preservationType) {
        String id = getId();
        request.putSampleIfAbsent(getPooledNormalSample(id, preservationType, instrumentType));

        return id;
    }

    private PooledNormalSample getPooledNormalSample(String pooledNormalCorrectedId, Preservation
            preservationType, InstrumentType instrumentType) {
        PooledNormalSample pooledNormalSample = new PooledNormalSample(getId());
        pooledNormalSample.setCmoSampleId(pooledNormalCorrectedId);
        pooledNormalSample.put(Constants.SPECIMEN_PRESERVATION_TYPE, preservationType.toString());
        pooledNormalSample.addSeqName(instrumentType.getValue());

        return pooledNormalSample;
    }

    private Sample getNormalSample(InstrumentType instrumentType, Preservation preservationType, String
            tissueSite, String correctedCmoId, String recipe) {
        Sample sample = getSample(instrumentType, preservationType, tissueSite, correctedCmoId, recipe);
        sample.setIsTumor(false);

        return sample;
    }

    private Sample getTumorSample(InstrumentType instrumentType, Preservation preservationType, String
            tissueSite, String correctedCmoId, String recipe) {
        Sample sample = getSample(instrumentType, preservationType, tissueSite, correctedCmoId, recipe);
        sample.setIsTumor(true);

        return sample;
    }

    private Sample getSample(InstrumentType instrumentType, Preservation preservationType, String
            tissueSite, String correctedCmoId, String recipe) {
        Sample sample = new Sample(getId());
        sample.addSeqName(instrumentType.getValue());
        sample.setCorrectedCmoId(correctedCmoId);
        sample.put(Constants.SPECIMEN_PRESERVATION_TYPE, preservationType.toString());
        sample.put(Constants.TISSUE_SITE, tissueSite);
        sample.setRecipe(recipe);

        return sample;
    }

    private String getId() {
        return String.valueOf(id++);
    }

}