package org.mskcc.kickoff.generator;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.Patient;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.PooledNormalSample;
import org.mskcc.domain.sample.Sample;
import org.mskcc.domain.sample.SpecimenPreservationType;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.util.Constants;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mskcc.domain.instrument.InstrumentType.*;
import static org.mskcc.domain.sample.SpecimenPreservationType.*;

public class SmartPairingRetrieverTest {
    private static final String TISSUE_SITE_1 = "TissueSite1";
    private static final String TISSUE_SITE_2 = "TissueSite2";
    private static final String PATIEND_ID_1 = "pat1";
    private static final String PATIEND_ID_2 = "pat2";
    private static final String PATIEND_ID_3 = "pat3";
    private BiPredicate<Sample, Sample> pairingInfoValidPredicate = (s1, s2) -> Objects.equals(s1.getSeqName(),
            s2.getSeqName());

    private SmartPairingRetriever smartPairingRetriever = new SmartPairingRetriever(pairingInfoValidPredicate);
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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
        String normalCorrectedCmoId = addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_1);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedCmoId);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    private String addNormalSample(Patient patient, InstrumentType instrumentType, SpecimenPreservationType
            preservationType, String tissueSite) {
        String id = getId();
        patient.addSample(getNormalSample(instrumentType, preservationType, tissueSite, id));

        return id;
    }

    @Test
    public void whenTumorAndNormalSamePreservationTissueSiteIsDifferent_shouldMatchByPreservation() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
        String normalCorrectedCmoId = addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_2);

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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
        String normalCorrectedCmoId = addNormalSample(patient, HISEQ, BLOOD, TISSUE_SITE_2);

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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
        addNormalSample(patient, NOVASEQ, BLOOD, TISSUE_SITE_2);

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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
        String normalCorrectedCmoId1 = addNormalSample(patient, HISEQ, BLOOD, TISSUE_SITE_2);
        addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1);

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
        String tumorCorrectedCmoId = addTumorSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1);
        addNormalSample(patient, NOVASEQ, BLOOD, TISSUE_SITE_2);
        String normalCorrectedId2 = addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_2);

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
        String tumorCorrectedCmoId = addTumorSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1);
        addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_2);
        String normalCorrectedIdCmo1 = addNormalSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1);

        //when
        Map<String, String> smartPairings = smartPairingRetriever.retrieve(request);

        //then
        Map<String, String> expected = Maps.newHashMap(tumorCorrectedCmoId, normalCorrectedIdCmo1);
        assertThat(smartPairings.size()).isEqualTo(1);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    private String addTumorSample(Patient patient, InstrumentType instrumentType, SpecimenPreservationType
            preservationType, String tissueSite) {
        String id = getId();
        patient.addSample(getTumorSample(instrumentType, preservationType, tissueSite, id));

        return id;
    }

    @Test
    public void whenOneFFPETumorAndFFPEPooledNormal_shouldReturnOnePairing() throws Exception {
        //given
        Patient patient = request.putPatientIfAbsent(PATIEND_ID_1);
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, BLOOD, TISSUE_SITE_1);
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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, TRIZOL, TISSUE_SITE_1);
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
        String tumorCorrectedCmoId = addTumorSample(patient, HISEQ, FFPE, TISSUE_SITE_1);
        addPooledNormal(HISEQ, FFPE);
        String normalCmoId = addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_1);

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
        String tumorCorrectedCmoId = addTumorSample(patient, NOVASEQ, FFPE, TISSUE_SITE_1);
        String pooledNormalCmoId = addPooledNormal(NOVASEQ, FFPE);
        addNormalSample(patient, HISEQ, FFPE, TISSUE_SITE_1);

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
        String pat1Tum1 = addTumorSample(patient1, NOVASEQ, FFPE, TISSUE_SITE_1);
        String pat1Tum2 = addTumorSample(patient1, HISEQ, FFPE, TISSUE_SITE_1);
        String pat1Tum3 = addTumorSample(patient1, HISEQ, FFPE, TISSUE_SITE_1);
        String pat1Tum4 = addTumorSample(patient1, MISEQ, FFPE, TISSUE_SITE_1);
        String pat1Tum5 = addTumorSample(patient1, MISEQ, FFPE, TISSUE_SITE_1);

        String pat1Norm1 = addNormalSample(patient1, NOVASEQ, BLOOD, TISSUE_SITE_1);
        String pat1Norm2 = addNormalSample(patient1, HISEQ, FFPE, TISSUE_SITE_2);
        String pat1Norm3 = addNormalSample(patient1, NOVASEQ, FFPE, TISSUE_SITE_1);


        Patient patient2 = request.putPatientIfAbsent(PATIEND_ID_2);
        String pat2Tum1 = addTumorSample(patient2, NOVASEQ, BLOOD, TISSUE_SITE_1);
        String pat2Tum2 = addTumorSample(patient2, HISEQ, FFPE, TISSUE_SITE_1);
        String pat2Tum3 = addTumorSample(patient2, HISEQ, FFPE, TISSUE_SITE_2);
        String pat2Tum4 = addTumorSample(patient2, MISEQ, FFPE, TISSUE_SITE_2);

        String pat2Norm1 = addNormalSample(patient2, NOVASEQ, BLOOD, TISSUE_SITE_1);
        String pat2Norm2 = addNormalSample(patient2, HISEQ, FFPE, TISSUE_SITE_2);


        Patient patient3 = request.putPatientIfAbsent(PATIEND_ID_3);
        String pat3Tum1 = addTumorSample(patient3, NOVASEQ, FFPE, TISSUE_SITE_1);
        String pat3Tum2 = addTumorSample(patient3, HISEQ, FFPE, TISSUE_SITE_1);
        String pat3Tum3 = addTumorSample(patient3, HISEQ, FFPE, TISSUE_SITE_2);
        String pat3Tum4 = addTumorSample(patient3, HISEQ, FFPE, TISSUE_SITE_1);

        String pat3Norm2 = addNormalSample(patient3, HISEQ, FFPE, TISSUE_SITE_1);
        String pat3Norm3 = addNormalSample(patient3, HISEQ, FFPE, TISSUE_SITE_2);
        String pat3Norm4 = addNormalSample(patient3, MISEQ, FFPE, TISSUE_SITE_1);

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

                .put(pat2Tum1, pat2Norm1)
                .put(pat2Tum2, pat2Norm2)
                .put(pat2Tum3, pat2Norm2)
                .put(pat2Tum4, pooledNormal1)

                .put(pat3Tum1, Constants.NA_LOWER_CASE)
                .put(pat3Tum2, pat3Norm2)
                .put(pat3Tum3, pat3Norm3)
                .put(pat3Tum4, pat3Norm2)
                .build();

        assertThat(smartPairings.size()).isEqualTo(13);
        assertThat(smartPairings).containsAllEntriesOf(expected);
    }

    private String addPooledNormal(InstrumentType instrumentType, SpecimenPreservationType preservationType) {
        String id = getId();
        request.putSampleIfAbsent(getPooledNormalSample(id, preservationType, instrumentType));

        return id;
    }

    private PooledNormalSample getPooledNormalSample(String pooledNormalCorrectedId, SpecimenPreservationType
            preservationType, InstrumentType instrumentType) {
        PooledNormalSample pooledNormalSample = new PooledNormalSample(getId());
        pooledNormalSample.setCmoSampleId(pooledNormalCorrectedId);
        pooledNormalSample.put(Constants.SPECIMEN_PRESERVATION_TYPE, preservationType.toString());
        pooledNormalSample.setSeqName(instrumentType.getValue());

        return pooledNormalSample;
    }

    private Sample getNormalSample(InstrumentType instrumentType, SpecimenPreservationType preservationType, String
            tissueSite, String correctedCmoId) {
        Sample sample = getSample(instrumentType, preservationType, tissueSite, correctedCmoId);
        sample.setIsTumor(false);

        return sample;
    }

    private Sample getTumorSample(InstrumentType instrumentType, SpecimenPreservationType preservationType, String
            tissueSite, String correctedCmoId) {
        Sample sample = getSample(instrumentType, preservationType, tissueSite, correctedCmoId);
        sample.setIsTumor(true);

        return sample;
    }

    private Sample getSample(InstrumentType instrumentType, SpecimenPreservationType preservationType, String
            tissueSite, String correctedCmoId) {
        Sample sample = new Sample(getId());
        sample.setSeqName(instrumentType.getValue());
        sample.setCorrectedCmoId(correctedCmoId);
        sample.put(Constants.SPECIMEN_PRESERVATION_TYPE, preservationType.toString());
        sample.put(Constants.TISSUE_SITE, tissueSite);

        return sample;
    }

    private String getId() {
        return String.valueOf(id++);
    }

}