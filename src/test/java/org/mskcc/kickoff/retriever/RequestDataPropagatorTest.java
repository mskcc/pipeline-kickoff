package org.mskcc.kickoff.retriever;

import com.velox.sapioutils.shared.utilities.Sets;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.CmoSampleInfo;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.InMemoryErrorRepository;

import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RequestDataPropagatorTest {
    private ErrorRepository errorRepository = new InMemoryErrorRepository();
    private RequestDataPropagator requestDataPropagator = new RequestDataPropagator("", "", errorRepository,
            (bs1, bs2) -> true);

    @Test
    public void whenAnyRequest_shouldAddAssay() throws Exception {
        assertAssay(RequestType.OTHER, Optional.empty(), "");
        assertAssay(RequestType.RNASEQ, Optional.empty(), "");
        assertAssay(RequestType.EXOME, Optional.empty(), "");
        assertAssay(RequestType.IMPACT, Optional.empty(), "");

        assertAssay(RequestType.OTHER, Optional.of(Constants.EMPTY), Constants.NA);
        assertAssay(RequestType.RNASEQ, Optional.of(Constants.EMPTY), Constants.NA);
        assertAssay(RequestType.EXOME, Optional.of(Constants.EMPTY), Constants.NA);
        assertAssay(RequestType.IMPACT, Optional.of(Constants.EMPTY), Constants.NA);

        String baitVersion1 = "someBait version";
        String baitVersion2 = "WES";
        String impactBait = "IMPACT410";
        assertAssay(RequestType.OTHER, Optional.of(baitVersion1), baitVersion1);
        assertAssay(RequestType.RNASEQ, Optional.of(baitVersion2), baitVersion2);
        assertAssay(RequestType.EXOME, Optional.of(baitVersion1), baitVersion1);
        assertAssay(RequestType.IMPACT, Optional.of(impactBait), impactBait);
    }

    @Test
    public void whenPMisNOPMandPatientIdIsEmpty_shouldAddErrorToErrorRespository() throws Exception {
        //given
        KickoffRequest kickoffRequest = new KickoffRequest("id", new NormalProcessingType(mock(ProjectFilesArchiver
                .class)));
        Sample sampleMock = new Sample("123", s -> true);
        Map<String, Object> properties = new HashMap<>();
        properties.put(Constants.ProjectInfo.PROJECT_MANAGER, Constants.NO_PM);
        properties.put(CmoSampleInfo.CMO_PATIENT_ID, Constants.EMPTY);
        properties.put(CmoSampleInfo.PATIENT_ID, "");

        CmoSampleInfo cmoSampleInfo = new CmoSampleInfo();
        cmoSampleInfo.setFields(properties);
        sampleMock.setCmoSampleInfo(cmoSampleInfo);
        kickoffRequest.putSampleIfAbsent(sampleMock);

        //when
        requestDataPropagator.addSamplesToPatients(kickoffRequest);

        //then
        assertThat(errorRepository.getErrors().size(), is(1));
        assertThat(errorRepository.getErrors().get(0).getErrorCode(), is(ErrorCode.EMPTY_PATIENT));
    }

    @Test
    public void whenPMisValidAndPatientIdIsEmpty_shouldAddErrorToErrorRespository() throws Exception {
        //given
        KickoffRequest kickoffRequest = new KickoffRequest("id", new NormalProcessingType(mock(ProjectFilesArchiver
                .class)));
        Sample sampleMock = new Sample("123", s -> true);
        Map<String, Object> properties = new HashMap<>();
        properties.put(Constants.ProjectInfo.PROJECT_MANAGER, Constants.NO_PM);
        properties.put(CmoSampleInfo.CMO_PATIENT_ID, Constants.EMPTY);

        CmoSampleInfo cmoSampleInfo = new CmoSampleInfo();
        cmoSampleInfo.setFields(properties);
        sampleMock.setCmoSampleInfo(cmoSampleInfo);
        kickoffRequest.putSampleIfAbsent(sampleMock);

        //when
        requestDataPropagator.addSamplesToPatients(kickoffRequest);

        //then
        assertThat(errorRepository.getErrors().size(), is(1));
        assertThat(errorRepository.getErrors().get(0).getErrorCode(), is(ErrorCode.EMPTY_PATIENT));
    }

    @Test
    public void whenPatientIdIsNotEmpty_shouldNotAddErrorToErrorRespository() throws Exception {
        //given
        KickoffRequest kickoffRequest = new KickoffRequest("id", mock(ProcessingType.class));
        Sample sampleMock = mock(Sample.class);
        when(sampleMock.isValid()).thenReturn(true);
        Map<String, String> properties = new HashMap<>();
        properties.put(Constants.CMO_PATIENT_ID, "C-123456");
        sampleMock.setProperties(properties);

        kickoffRequest.putSampleIfAbsent(sampleMock);

        //when
        requestDataPropagator.addSamplesToPatients(kickoffRequest);

        //then
        assertThat(errorRepository.getErrors().size(), is(0));
    }

    @Test
    public void whenThereAreMultipleSpecies_shouldConcatThem() throws Exception {
        //given
        KickoffRequest request = new KickoffRequest("id1", new ForcedProcessingType());
        Sample sample1 = new Sample("id1", s -> true);
        sample1.put(Constants.SPECIES, "Human,Zebrafish");

        Sample sample2 = new Sample("id2", s -> true);
        sample2.put(Constants.SPECIES, "Human");

        Sample sample3 = new Sample("id3", s -> true);
        sample3.put(Constants.SPECIES, "Human");

        request.putSampleIfAbsent(sample1);
        request.putSampleIfAbsent(sample2);
        request.putSampleIfAbsent(sample3);

        //when
        requestDataPropagator.propagateRequestData(Arrays.asList(request));

        //then
        Set<RequestSpecies> species1 = Sets.asHashSet(RequestSpecies.HUMAN);
        Set<RequestSpecies> species2 = Sets.asHashSet(RequestSpecies.HUMAN, RequestSpecies.ZEBRAFISH);
        Assertions.assertThat(request.getSpeciesSet()).containsOnly(species1, species2);
    }

    private void assertAssay(RequestType requestType, Optional<String> baitVersion, String expectedAssay) {
        //given
        KickoffRequest request = new KickoffRequest("id", mock(NormalProcessingType.class));
        request.setRequestType(requestType);
        request.setBaitVersion(null);

        if (baitVersion.isPresent())
            request.setBaitVersion(baitVersion.get());

        Map<String, String> projectInfo = new HashMap<>();

        //when
        requestDataPropagator.setAssay(request, projectInfo);

        //then
        assertThat(projectInfo.get(Constants.ASSAY), is(expectedAssay));
    }
}