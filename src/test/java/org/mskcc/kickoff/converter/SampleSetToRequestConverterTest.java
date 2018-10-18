package org.mskcc.kickoff.converter;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.*;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.process.ForcedProcessingType;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.sampleset.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;
import org.mskcc.kickoff.util.ConverterUtils;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.InMemoryErrorRepository;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.typeCompatibleWith;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.*;
import static org.mskcc.util.TestUtils.assertThrown;

public class SampleSetToRequestConverterTest {
    private static final String PATIENT_ID_1 = "patient1";
    private static final String PATIENT_ID_2 = "patient2";
    private static final String PATIENT_ID_3 = "patient3";
    private static final String PATIENT_ID_4 = "patient4";
    private static final String SAMPLE_ENTITY_NAME = "sample";
    private static final String PATIENT_ENTITY_NAME = "patient";
    private static final String POOL_ENTITY_NAME = "pool";
    private static int id = 0;
    private final ProjectFilesArchiver projectFilesArchiver = mock(ProjectFilesArchiver.class);
    private final NormalProcessingType normalProcessingType = new NormalProcessingType(projectFilesArchiver);
    private final SampleSetProjectInfoConverter sampleSetProjectInfoConverterMock = mock
            (SampleSetProjectInfoConverter.class);
    private ErrorRepository errorRepository = new InMemoryErrorRepository();
    private BiPredicate<String, String> baitSetPredicate = mock(BiPredicate.class);
    private SampleSetToRequestConverter sampleSetToRequestConverter = new SampleSetToRequestConverter
            (sampleSetProjectInfoConverterMock, baitSetPredicate, errorRepository);
    private KickoffSampleSet sampleSet;
    private RequestSpecies DEFAULT_SPECIES;

    @Before
    public void setUp() throws Exception {
        sampleSet = new KickoffSampleSet("3243");
        sampleSet.setPrimaryRequestId(String.format("request_%d", id));
    }

    @Test
    public void whenOneRequestIsForced_shouldProjectBeForced() {
        sampleSet.setKickoffRequests(Arrays.asList(getForcedImpactRequest(), getNormalImpactHumanPiRequest(),
                getNormalImpactHumanPiRequest()));

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getProcessingType().getClass(), typeCompatibleWith(ForcedProcessingType.class));
    }

    @Test
    public void whenAllRequestsAreNormal_shouldProjectBeNormal() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest(),
                getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest()));

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getProcessingType().getClass(), typeCompatibleWith(NormalProcessingType.class));
    }

    @Test
    public void whenAllRequestsAreForced_shouldProjectBeForced() {
        sampleSet.setKickoffRequests(Arrays.asList(getForcedImpactRequest(), getForcedImpactRequest(),
                getForcedImpactRequest()));

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getProcessingType().getClass(), typeCompatibleWith(ForcedProcessingType.class));
    }

    @Test
    public void whenRequestsHasNoSamples_shouldThrowException() {
        List<KickoffRequest> kickoffRequests = getRequestWithEntities(Arrays.asList(0), (r, id) -> r
                        .putSampleIfAbsent(id),
                "sample");

        sampleSet.setKickoffRequests(kickoffRequests);

        assertThatExceptionOfType(KickoffRequest.NoValidSamplesException.class).isThrownBy(() -> sampleSetToRequestConverter.convert(sampleSet));
    }

    @Test
    public void whenOneRequestsWithSamples_shouldProjectContainSameNumberOfSamples() {
        List<Integer> samplesCounts = Arrays.asList(3);
        List<KickoffRequest> kickoffRequests = getRequestWithEntities(samplesCounts, getPassedSampleFunction(),
                SAMPLE_ENTITY_NAME);
        sampleSet.setKickoffRequests(kickoffRequests);

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectHasAllEntities(request.getSamples(), samplesCounts, SAMPLE_ENTITY_NAME);
    }

    private BiConsumer<KickoffRequest, String> getPassedSampleFunction() {
        return (r, id) -> {
            Sample sample = r.putSampleIfAbsent(id);
            Run run1 = sample.putRunIfAbsent("run1");
            run1.setSampleLevelQcStatus(QcStatus.PASSED);
            run1.setPostQcStatus(QcStatus.PASSED);
        };
    }

    @Test
    public void whenTwoRequestsWithSamples_shouldProjectContainSamplesFromBoth() {
        List<Integer> samplesCounts = Arrays.asList(5, 7);
        sampleSet.setKickoffRequests(getRequestWithEntities(samplesCounts, getPassedSampleFunction(),
                SAMPLE_ENTITY_NAME));

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectHasAllEntities(request.getSamples(), samplesCounts, SAMPLE_ENTITY_NAME);
    }

    @Test
    public void whenTwoRequestsHaveNoLibTypes_shouldProjectContainNoLibTypes() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getLibTypes(), Arrays.asList());
    }

    @Test
    public void whenTwoRequestsWithLibTypes_shouldProjectContainLibTypesFromBoth() {
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        kickoffRequest1.addLibType(LibType.KAPA_M_RNA_STRANDED);
        kickoffRequest1.addLibType(LibType.SMARTER_AMPLIFICATION);

        KickoffRequest kickoffRequest2 = getNormalImpactHumanPiRequest();
        kickoffRequest2.addLibType(LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED);
        kickoffRequest2.addLibType(LibType.TRU_SEQ_SM_RNA);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest1, kickoffRequest2));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getLibTypes(), Arrays.asList(LibType.KAPA_M_RNA_STRANDED, LibType
                .SMARTER_AMPLIFICATION, LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED, LibType.TRU_SEQ_SM_RNA));
    }

    @Test
    public void whenTwoRequestsWithRepeatingLibTypes_shouldProjectContainNonRepeatingLibTypesFromBoth() {
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        kickoffRequest1.addLibType(LibType.KAPA_M_RNA_STRANDED);
        kickoffRequest1.addLibType(LibType.SMARTER_AMPLIFICATION);

        KickoffRequest kickoffRequest2 = getNormalImpactHumanPiRequest();
        kickoffRequest2.addLibType(LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED);
        kickoffRequest2.addLibType(LibType.TRU_SEQ_SM_RNA);
        kickoffRequest2.addLibType(LibType.SMARTER_AMPLIFICATION);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest1, kickoffRequest2));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getLibTypes(), Arrays.asList(LibType.KAPA_M_RNA_STRANDED, LibType
                .SMARTER_AMPLIFICATION, LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED, LibType.TRU_SEQ_SM_RNA));
    }

    @Test
    public void whenTwoRequestsWithPools_shouldProjectContainPoolsFromBoth() {
        List<Integer> poolsCounts = Arrays.asList(4, 3);
        List<KickoffRequest> kickoffRequests = getRequestWithEntities(poolsCounts, (r, id) -> r.putPoolIfAbsent(id),
                POOL_ENTITY_NAME);
        addPassedSample(kickoffRequests.get(0));

        sampleSet.setKickoffRequests(kickoffRequests);
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectHasAllEntities(request.getPools(), poolsCounts, POOL_ENTITY_NAME);
    }

    @Test
    public void whenRequestsHaveNoStrand_shouldProjectContainNoStrand() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getStrands().size(), is(0));
    }

    @Test
    public void whenRequestsHaveOneStrand_shouldProjectContainThisStrand() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        kickoffRequest.setStrands(new HashSet<>(Arrays.asList(Strand.REVERSE)));
        kickoffRequest1.setStrands(new HashSet<>(Arrays.asList(Strand.REVERSE)));

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getStrands().size(), is(1));
        assertThat(request.getStrands().contains(Strand.REVERSE), is(true));
    }

    @Test
    public void whenOneRequestHasMultipleDifferentStrand_shouldThrowAnException() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        kickoffRequest.setStrands(new HashSet<>(Arrays.asList(Strand.REVERSE, Strand.EMPTY)));

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest));
        Optional<Exception> exception = assertThrown(() -> sampleSetToRequestConverter.convert(sampleSet));
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(SampleSetToRequestConverter
                .AmbiguousStrandException.class));
    }

    @Test
    public void whenRequestsHaveMultipleDifferentStrand_shouldThrowAnException() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        kickoffRequest.setStrands(new HashSet<>(Collections.singletonList(Strand.REVERSE)));
        kickoffRequest1.setStrands(new HashSet<>(Collections.singletonList(Strand.EMPTY)));

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        Optional<Exception> exception = assertThrown(() -> sampleSetToRequestConverter.convert(sampleSet));
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(SampleSetToRequestConverter
                .AmbiguousStrandException.class));
    }

    @Test
    public void whenRequestsHaveNoRequestType_shouldThrowAnException() {
        KickoffRequest kickoffRequest = getHumanPiRequest();
        KickoffRequest kickoffRequest1 = getHumanPiRequest();

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        Optional<Exception> exception = assertThrown(() -> sampleSetToRequestConverter.convert(sampleSet));
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(SampleSetToRequestConverter.NoRequestType.class));
    }

    @Test
    public void whenRequestsHaveMultipleDifferentRequestType_shouldThrowAnException() {
        KickoffRequest kickoffRequest = getHumanPiRequest();
        KickoffRequest kickoffRequest1 = getHumanPiRequest();

        kickoffRequest.setRequestType(RequestType.EXOME);
        kickoffRequest1.setRequestType(RequestType.IMPACT);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        Optional<Exception> exception = assertThrown(() -> sampleSetToRequestConverter.convert(sampleSet));
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(SampleSetToRequestConverter.AmbiguousRequestType
                .class));
    }

    @Test
    public void whenRequestsHaveSameRequestType_shouldProjectHaveThisRequestTypeSet() {
        KickoffRequest kickoffRequest = getHumanPiRequest();
        KickoffRequest kickoffRequest1 = getHumanPiRequest();

        kickoffRequest.setRequestType(RequestType.EXOME);
        kickoffRequest1.setRequestType(RequestType.EXOME);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getRequestType(), is(RequestType.EXOME));
    }

    @Test
    public void whenRequestsHaveNoReadMe_shouldProjectHaveNoReadMe() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getReadMe(), is(StringUtils.EMPTY));
    }

    @Test
    public void whenOneRequestHasReadme_shouldProjectContainThisReadMe() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String readMe = "some readme";
        kickoffRequest.setReadMe(readMe);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getReadMe(), is(kickoffRequest.getId() + ": " + readMe));
    }

    @Test
    public void whenMultipleRequestsHaveReadme_shouldProjectContainThoseReadMeConcatenated() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String readMe = "some readme";
        kickoffRequest.setReadMe(readMe);

        String readMe1 = "some other readme";
        kickoffRequest1.setReadMe(readMe1);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getReadMe(), is(String.format("%s: %s\n%s: %s", kickoffRequest.getId(), readMe,
                kickoffRequest1.getId(), readMe1)));
    }

    @Test
    public void whenAllRequestAreNotMannualDemux_shouldProjectNotBeManualDemux() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest(),
                getForcedImpactRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.isManualDemux(), is(false));
    }

    @Test
    public void whenAllRequestMannualDemux_shouldProjectBeManualDemux() {
        sampleSet.setKickoffRequests(Arrays.asList(getMannualDemuxRequest(), getMannualDemuxRequest(),
                getMannualDemuxRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.isManualDemux(), is(true));
    }

    @Test
    public void whenOneRequestMannualDemux_shouldProjectBeManualDemux() {
        sampleSet.setKickoffRequests(Arrays.asList(getMannualDemuxRequest(), getNormalImpactHumanPiRequest(),
                getNormalImpactHumanPiRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.isManualDemux(), is(true));
    }

    @Test
    public void whenAllRequestAreNotBicAutorunnable_shouldProjectBeNotBicAutorunnable() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest(),
                getForcedImpactRequest()));

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.isBicAutorunnable(), is(false));
    }

    @Test
    public void whenAllRequestBicAutorunnable_shouldProjectBeBicAutorunnable() {
        sampleSet.setKickoffRequests(Arrays.asList(getBicAutorunnableRequest(), getBicAutorunnableRequest(),
                getBicAutorunnableRequest()));

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.isBicAutorunnable(), is(true));
    }

    @Test
    public void whenOneRequestNotBicAutorunnable_shouldProjectNotBeBicAutorunnable() {
        sampleSet.setKickoffRequests(Arrays.asList(getBicAutorunnableRequest(), getBicAutorunnableRequest(),
                getNormalImpactHumanPiRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.isBicAutorunnable(), is(false));
    }

    @Test
    public void whenRequestsHaveNoReadMeInfo_shouldProjectHaveNoReadMeInfo() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getReadmeInfo(), is(StringUtils.EMPTY));
    }

    @Test
    public void whenOneRequestHasReadmeInfo_shouldProjectContainThisReadMeInfo() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String readMe = "some readme";
        kickoffRequest.setReadmeInfo(readMe);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getReadmeInfo(), is(kickoffRequest.getId() + ": " + readMe));
    }

    @Test
    public void whenMultipleRequestsHaveReadmeInfo_shouldProjectContainThoseReadMeInfoConcatenated() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String readMe = "some readme";
        kickoffRequest.setReadmeInfo(readMe);

        String readMe1 = "some other readme";
        kickoffRequest1.setReadmeInfo(readMe1);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getReadmeInfo(), is(String.format("%s: %s\n%s: %s", kickoffRequest.getId(), readMe,
                kickoffRequest1.getId(), readMe1)));
    }

    @Test
    public void whenRequestHaveRunNumbers_shouldProjectContainThoseRunnumbersConcatenated() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        kickoffRequest.setRunNumber(1);
        kickoffRequest1.setRunNumber(2);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getRunNumbers(), is(String.format("%s: %s,%s: %s", kickoffRequest.getId(), 1,
                kickoffRequest1.getId(), 2)));
    }

    @Test
    public void whenRequestsHaveNoExtraReadMeInfo_shouldProjectHaveNoExtraReadMeInfo() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getExtraReadMeInfo(), is(StringUtils.EMPTY));
    }

    @Test
    public void whenOneRequestHasExtraReadmeInfo_shouldProjectContainThisExtraReadMeInfo() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String readMe = "some readme";
        kickoffRequest.setExtraReadMeInfo(readMe);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getExtraReadMeInfo(), is(kickoffRequest.getId() + ": " + readMe));
    }

    @Test
    public void whenMultipleRequestsHaveExtraReadmeInfo_shouldProjectContainThoseExtraReadMeInfoConcatenated() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String readMe = "some readme";
        kickoffRequest.setExtraReadMeInfo(readMe);

        String readMe1 = "some other readme";
        kickoffRequest1.setExtraReadMeInfo(readMe1);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getExtraReadMeInfo(), is(String.format("%s: %s\n%s: %s", kickoffRequest.getId(), readMe,
                kickoffRequest1.getId(), readMe1)));
    }

    @Test
    public void whenAllRequestsHaveSameSpecies_shouldProjectHaveThisSpeciesSet() {
        //given
        KickoffRequest kickoffRequest = getNormalImpactPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactPiRequest();

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertThat(request.getSpeciesSet(), containsInAnyOrder(Sets.newHashSet(DEFAULT_SPECIES)));
    }

    @Test
    public void whenRequestsHaveDifferentSpecies_shouldNotThrowAnException() {
        //given
        KickoffRequest kickoffRequest = getNormalImpactPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactPiRequest();

        kickoffRequest.setSpeciesSet(getSpeciesSet(RequestSpecies.BACTERIA));
        kickoffRequest1.setSpeciesSet(getSpeciesSet(RequestSpecies.C_ELEGANS));

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));

        //when
        Optional<Exception> exception = assertThrown(() -> sampleSetToRequestConverter.convert(sampleSet));

        //when
        assertThat(exception.isPresent(), is(false));
    }

    private Set<Set<RequestSpecies>> getSpeciesSet(RequestSpecies requestSpecies) {
        Set<Set<RequestSpecies>> species = new HashSet<>();
        species.add(Sets.newHashSet(requestSpecies));

        return species;
    }

    @Test
    public void whenRequestsHaveNoSpecies_shouldThrowAnException() {
        //given
        KickoffRequest request1 = getNormalImpactPiRequest();
        request1.setSpeciesSet(Collections.emptySet());
        KickoffRequest request2 = getNormalImpactPiRequest();
        request2.setSpeciesSet(Collections.emptySet());

        sampleSet.setKickoffRequests(Arrays.asList(request1, request2));

        //when
        Optional<Exception> exception = assertThrown(() -> sampleSetToRequestConverter.convert(sampleSet));

        //then
        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), typeCompatibleWith(ConverterUtils.RequiredPropertyNotSetException
                .class));
    }

    @Test
    public void whenTwoRequestsHaveNoRunIds_shouldProjectContainNoRunIds() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getRunIds().containsAll(Collections.emptyList()), is(true));
    }

    @Test
    public void whenTwoRequestsWithRunIds_shouldProjectContainRunIdsFromBoth() {
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        String run1 = "run1";
        String run2 = "run2";
        String run3 = "run3";
        String run4 = "run4";

        kickoffRequest1.addRunID(run1);
        kickoffRequest1.addRunID(run2);

        KickoffRequest kickoffRequest2 = getNormalImpactHumanPiRequest();
        kickoffRequest2.addRunID(run3);
        kickoffRequest2.addRunID(run4);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest1, kickoffRequest2));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getRunIds(), Arrays.asList(run1, run2, run3, run4));
    }

    private <T> void assertProjectList(Set<T> actual, List<T> expected) {
        assertThat(actual.size(), is(expected.size()));
        assertThat(actual.containsAll(expected), is(true));
    }

    @Test
    public void whenTwoRequestsWithRepeatingRunIds_shouldProjectContainNonRepeatingRunIdsFromBoth() {
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        String run1 = "run1";
        String run2 = "run2";
        String run3 = "run3";

        kickoffRequest1.addRunID(run1);
        kickoffRequest1.addRunID(run2);

        KickoffRequest kickoffRequest2 = getNormalImpactHumanPiRequest();
        kickoffRequest2.addRunID(run1);
        kickoffRequest2.addRunID(run3);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest1, kickoffRequest2));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getRunIds().containsAll(Arrays.asList(run1, run2, run3)), is(true));
    }

    @Test
    public void whenAllRequestsHaveSameProjectInvestigator_shouldProjectHaveThisProjectInvestigatorSet() {
        KickoffRequest kickoffRequest = getNormalImpactHumanRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanRequest();

        String kingJulianInvestigator = "King Julian";
        kickoffRequest.setPi(kingJulianInvestigator);
        kickoffRequest1.setPi(kingJulianInvestigator);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getPi(), is(kingJulianInvestigator));
    }

    @Test
    public void whenRequestsHaveNoInvest_shouldProjectHaveNoInvest() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        sampleSet.setRequests(Arrays.asList(kickoffRequest, kickoffRequest1));

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getInvest(), is(StringUtils.EMPTY));
    }

    @Test
    public void whenOneRequestHasInvest_shouldProjectContainThisInvest() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String invest = "some invest";
        kickoffRequest.setInvest(invest);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getInvest(), is(kickoffRequest.getId() + ": " + invest));
    }

    @Test
    public void whenMultipleRequestsHaveInvest_shouldProjectContainThoseInvestConcatenated() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();

        String invest = "some invest";
        kickoffRequest.setInvest(invest);

        String invest1 = "some other invest";
        kickoffRequest1.setInvest(invest1);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest, kickoffRequest1));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);
        assertThat(request.getInvest(), is(String.format("%s: %s,%s: %s", kickoffRequest.getId(), invest,
                kickoffRequest1.getId(), invest1)));
    }

    @Test
    public void whenTwoRequestsHaveNoAmpTypes_shouldProjectContainNoAmpTypes() {
        sampleSet.setKickoffRequests(Arrays.asList(getNormalImpactHumanPiRequest(), getNormalImpactHumanPiRequest()));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getAmpTypes(), Arrays.asList());
    }

    @Test
    public void whenTwoRequestsWithAmpTypes_shouldProjectContainAmpTypesFromBoth() {
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        String amp1 = "amp1";
        String amp2 = "amp2";
        String amp3 = "amp3";
        String amp4 = "amp4";

        kickoffRequest1.addAmpType(amp1);
        kickoffRequest1.addAmpType(amp2);

        KickoffRequest kickoffRequest2 = getNormalImpactHumanPiRequest();
        kickoffRequest2.addAmpType(amp3);
        kickoffRequest2.addAmpType(amp4);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest1, kickoffRequest2));
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getAmpTypes(), Arrays.asList(amp1, amp2, amp3, amp4));
    }

    @Test
    public void whenTwoRequestsWithRepeatingAmpTypes_shouldProjectContainNonRepeatingAmpTypesFromBoth() {
        KickoffRequest kickoffRequest1 = getNormalImpactHumanPiRequest();
        String amp1 = "amp1";
        String amp2 = "amp2";
        String amp3 = "amp3";

        kickoffRequest1.addAmpType(amp1);
        kickoffRequest1.addAmpType(amp2);

        KickoffRequest kickoffRequest2 = getNormalImpactHumanPiRequest();
        kickoffRequest2.addAmpType(amp3);
        kickoffRequest2.addAmpType(amp1);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest1, kickoffRequest2));

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectList(request.getAmpTypes(), Arrays.asList(amp1, amp2, amp3));
    }

    @Test
    public void whenRequestsHasNoPatients_shouldProjectContainNoPatients() {
        List<KickoffRequest> kickoffRequests = getRequestWithEntities(Arrays.asList(0), (r, id) -> r
                .putPatientIfAbsent(id), PATIENT_ENTITY_NAME);

        addPassedSample(kickoffRequests.get(0));
        sampleSet.setKickoffRequests(kickoffRequests);

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getPatients().size(), is(0));
    }

    @Test
    public void whenSampleSetHasOneRequestWithOnePatientWithOneSample_shouldProjectContainOnePatientWithOneSample() {
        //given
        KickoffRequest req1 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 1, PATIENT_ID_1);

        List<KickoffRequest> requests = Arrays.asList(req1);
        sampleSet.setKickoffRequests(requests);

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertContainsPatientsWithSamples(request, requests);
    }


    @Test
    public void whenThereAreThreeRequestsWithMultiplePatients_shouldProjectContainPatientsFromAll() {
        List<Integer> patientsCounts = Arrays.asList(2, 9, 4);
        List<KickoffRequest> kickoffRequests = getRequestWithEntities(patientsCounts, (r, id) -> r
                .putPatientIfAbsent(id), PATIENT_ENTITY_NAME);

        addPassedSample(kickoffRequests.get(0));

        sampleSet.setKickoffRequests(kickoffRequests);

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertProjectHasAllEntities(request.getPatients(), patientsCounts, PATIENT_ENTITY_NAME);
    }

    @Test
    public void whenThereIsOneRequestWithTwoPatients_patientsShouldHaveDifferentGroupsAndHaveSamplesForEach() throws
            Exception {
        //given
        KickoffRequest req1 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 1, PATIENT_ID_1);
        addSamplesToPatient(req1, 1, PATIENT_ID_2);

        List<KickoffRequest> requests = Arrays.asList(req1);
        sampleSet.setKickoffRequests(requests);

        //when
        KickoffRequest kickoffRequest = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertContainsPatientsWithSamples(kickoffRequest, requests);
    }

    @Test
    public void whenThereAreTwoRequestsWithSamePatient_thisPatientShouldBeInOneGroupAndContainSamplesFromBoth()
            throws Exception {
        //given
        KickoffRequest req1 = getNormalImpactHumanPiRequest();
        KickoffRequest req2 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 2, PATIENT_ID_1);
        addSamplesToPatient(req2, 2, PATIENT_ID_1);

        List<KickoffRequest> requests = Arrays.asList(req1, req2);
        sampleSet.setKickoffRequests(requests);

        //when
        KickoffRequest kickoffRequest = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertContainsPatientsWithSamples(kickoffRequest, requests);
    }

    @Test
    public void whenThereAreMultipleRequestsWithMultiplePatients_requestShouldContainAllPatientsWithTheirSamples()
            throws Exception {
        //given
        KickoffRequest req1 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 2, PATIENT_ID_1);
        addSamplesToPatient(req1, 1, PATIENT_ID_2);

        KickoffRequest req2 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 10, PATIENT_ID_1);
        addSamplesToPatient(req1, 9, PATIENT_ID_3);
        addSamplesToPatient(req1, 3, PATIENT_ID_3);

        KickoffRequest req3 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 1, PATIENT_ID_1);
        addSamplesToPatient(req1, 1, PATIENT_ID_3);
        addSamplesToPatient(req1, 5, PATIENT_ID_4);

        KickoffRequest req4 = getNormalImpactHumanPiRequest();
        addSamplesToPatient(req1, 3, PATIENT_ID_2);
        addSamplesToPatient(req1, 17, PATIENT_ID_3);

        List<KickoffRequest> requests = Arrays.asList(req1, req2, req3, req4);
        sampleSet.setKickoffRequests(requests);

        //when
        KickoffRequest kickoffRequest = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertContainsPatientsWithSamples(kickoffRequest, requests);
    }

    private void assertContainsPatientsWithSamples(KickoffRequest resultRequest, List<KickoffRequest>
            originalRequests) {
        Map<String, List<Patient>> patientIdToPatients = originalRequests.stream()
                .flatMap(r -> r.getPatients().values().stream())
                .collect(Collectors.groupingBy(Patient::getPatientId));

        assertThat(resultRequest.getPatients().size(), is(patientIdToPatients.size()));

        Set<Integer> groups = new HashSet<>();
        for (List<Patient> patientList : patientIdToPatients.values())
            assertPatientContainsSamples(resultRequest, groups, patientList);

        assertThat(groups.size(), is(patientIdToPatients.size()));
    }

    private void assertPatientContainsSamples(KickoffRequest resultRequest, Set<Integer> groups, List<Patient>
            patientList) {
        String patientId = patientList.get(0).getPatientId();
        assertThat(resultRequest.getPatients().containsKey(patientId), is(true));

        Patient patient = resultRequest.getPatients().get(patientId);
        assertThat(patient.getPatientId(), is(patientId));

        groups.add(patient.getGroupNumber());

        List<Sample> patientSamples = patientList.stream()
                .flatMap(p -> p.getSamples().stream())
                .collect(Collectors.toList());
        Set<Sample> requestSamples = patient.getSamples();

        assertContainsAllSamples(patientSamples, requestSamples);
    }

    private void assertContainsAllSamples(List<Sample> patientSamples, Set<Sample> requestSamples) {
        assertThat(requestSamples.size(), is(patientSamples.size()));
        for (Sample sample : patientSamples) {
            assertThat(requestSamples.contains(sample), is(true));
        }
    }

    private void addSamplesToPatient(KickoffRequest request, int numberOfSamples, String patientId) {
        Patient patient = request.putPatientIfAbsent(patientId);

        for (int i = 0; i < numberOfSamples; i++)
            patient.addSample(getSample());
    }

    private Sample getSample() {
        return new Sample(String.valueOf(++id));
    }

    @Test
    public void whenConverting_shouldRequestHaveProjectInfoSet() {
        Map<String, String> projectInfo = new LinkedHashMap<>();
        when(sampleSetProjectInfoConverterMock.convert(sampleSet)).thenReturn(projectInfo);

        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getProjectInfo(), is(projectInfo));
    }

    @Test
    public void whenOneRequestContainsAllProperties_shouldProjectContainAllThoseProperties() {
        KickoffRequest kickoffRequest = getEmptyRequest();
        kickoffRequest.setProcessingType(new ForcedProcessingType());

        String sampleId1 = "12345_S_1";
        kickoffRequest.putSampleIfAbsent(sampleId1);
        String sampleId2 = "12345_S_2";
        kickoffRequest.putSampleIfAbsent(sampleId2);
        String sampleId3 = "12345_S_3";
        kickoffRequest.putSampleIfAbsent(sampleId3);

        String poolId1 = "pool_1";
        kickoffRequest.putPoolIfAbsent(poolId1);
        String poolId2 = "pool_2";
        kickoffRequest.putPoolIfAbsent(poolId2);
        String poolId3 = "pool_3";
        kickoffRequest.putPoolIfAbsent(poolId3);
        String poolId4 = "pool_4";
        kickoffRequest.putPoolIfAbsent(poolId4);

        LibType libType1 = LibType.KAPA_M_RNA_STRANDED;
        kickoffRequest.addLibType(libType1);
        LibType libType2 = LibType.SMARTER_AMPLIFICATION;
        kickoffRequest.addLibType(libType2);

        Strand strand = Strand.REVERSE;
        kickoffRequest.addStrand(strand);

        RequestType reqType = RequestType.IMPACT;
        kickoffRequest.setRequestType(reqType);

        String readMe = "Read me now!";
        kickoffRequest.setReadMe(readMe);

        boolean manualDemux = true;
        kickoffRequest.setManualDemux(manualDemux);
        boolean bicAutorunnable = true;
        kickoffRequest.setBicAutorunnable(bicAutorunnable);

        Recipe recipe = Recipe.AMPLI_SEQ;
        kickoffRequest.setRecipe(recipe);

        String readMeInfo = "read me read me";
        kickoffRequest.setReadmeInfo(readMeInfo);

        int runNumber = 6;
        kickoffRequest.setRunNumber(runNumber);

        String extraRead = "I'm extra";
        kickoffRequest.setExtraReadMeInfo(extraRead);

        Set<Set<RequestSpecies>> species = getSpeciesSet(RequestSpecies.BACTERIA);
        kickoffRequest.setSpeciesSet(species);

        String runId = "runId3";
        kickoffRequest.addRunID(runId);

        String pi = "Kowalski";
        kickoffRequest.setPi(pi);

        String invest = "Morris";
        kickoffRequest.setInvest(invest);

        String ampType = "amplification";
        String ampType2 = "amplification2";
        kickoffRequest.addAmpType(ampType);
        kickoffRequest.addAmpType(ampType2);

        String baitVersion = "some bait version";
        kickoffRequest.setBaitVersion(baitVersion);

        String patientId1 = "patient1";
        kickoffRequest.putPatientIfAbsent(patientId1);
        String patientId2 = "patient2";
        kickoffRequest.putPatientIfAbsent(patientId2);

        sampleSet.setKickoffRequests(Arrays.asList(kickoffRequest));
        sampleSet.setRecipe(recipe);
        sampleSet.setBaitSet(baitVersion);
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        assertThat(request.getSamples().containsKey(sampleId1), is(true));
        assertThat(request.getSamples().containsKey(sampleId2), is(true));
        assertThat(request.getSamples().containsKey(sampleId3), is(true));

        assertThat(request.getPools().containsKey(poolId1), is(true));
        assertThat(request.getPools().containsKey(poolId2), is(true));
        assertThat(request.getPools().containsKey(poolId3), is(true));
        assertThat(request.getPools().containsKey(poolId4), is(true));

        assertThat(request.getLibTypes().containsAll(Arrays.asList(libType1, libType2)), is(true));

        assertThat(request.getStrands().contains(strand), is(true));
        assertThat(request.getRequestType(), is(reqType));

        assertThat(request.getReadMe(), is(kickoffRequest + ": " + readMe));

        assertThat(request.isManualDemux(), is(manualDemux));
        assertThat(request.isBicAutorunnable(), is(bicAutorunnable));

        assertThat(request.getRecipe(), is(recipe));
        assertThat(request.getReadmeInfo(), is(kickoffRequest.getId() + ": " + readMeInfo));

        assertThat(request.getRunNumbers(), is(kickoffRequest.getId() + ": " + String.valueOf(runNumber)));

        assertThat(request.getExtraReadMeInfo(), is(kickoffRequest.getId() + ": " + extraRead));

        assertThat(request.getSpeciesSet(), is(species));

        assertThat(request.getRunIds().contains(runId), is(true));

        assertThat(request.getPi(), is(pi));
        assertThat(request.getInvest(), is(kickoffRequest.getId() + ": " + invest));

        assertThat(request.getAmpTypes().containsAll(Arrays.asList(ampType, ampType2)), is(true));

        assertThat(request.getBaitVersion(), is(baitVersion));

        assertThat(request.getPatients().containsKey(patientId1), is(true));
        assertThat(request.getPatients().containsKey(patientId2), is(true));
    }

    @Test
    public void whenRequestsHaveIncompatibleBaitSets_shouldAddError() throws Exception {
        //given

        KickoffRequest kickoffRequest1 = getNormalImpactRequest();
        kickoffRequest1.setBaitVersion(Recipe.IMPACT_341.getValue());

        KickoffRequest kickoffRequest2 = getNormalImpactRequest();
        kickoffRequest2.setBaitVersion(Recipe.HEME_PACT_V_3.getValue());

        List<KickoffRequest> requests = Arrays.asList(kickoffRequest1, kickoffRequest2);

        sampleSet.setKickoffRequests(requests);
        when(baitSetPredicate.test(any(), any())).thenReturn(false);

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertThat(errorRepository.getErrors().stream()
                .anyMatch(e -> e.getErrorCode() == ErrorCode.BAIT_SET_NOT_COMPATIBLE), is(true));
    }

    @Test
    public void whenRequestsHaveCompatibleBaitSets_shouldAddError() throws Exception {
        //given
        KickoffRequest kickoffRequest1 = getNormalImpactRequest();
        kickoffRequest1.setBaitVersion(Recipe.IMPACT_341.getValue());

        KickoffRequest kickoffRequest2 = getNormalImpactRequest();
        kickoffRequest2.setBaitVersion(Recipe.HEME_PACT_V_3.getValue());

        List<KickoffRequest> requests = Arrays.asList(kickoffRequest1, kickoffRequest2);

        sampleSet.setKickoffRequests(requests);
        when(baitSetPredicate.test(any(), any())).thenReturn(true);

        //when
        KickoffRequest request = sampleSetToRequestConverter.convert(sampleSet);

        //then
        assertThat(errorRepository.getErrors().stream()
                .noneMatch(e -> e.getErrorCode() == ErrorCode.BAIT_SET_NOT_COMPATIBLE), is(true));
    }

    private KickoffRequest getBicAutorunnableRequest() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        kickoffRequest.setBicAutorunnable(true);
        return kickoffRequest;
    }

    private KickoffRequest getMannualDemuxRequest() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        kickoffRequest.setManualDemux(true);
        return kickoffRequest;
    }

    private void assertProjectHasAllEntities(Map<String, ?> actual, List<Integer> entityCounts, String entityName) {
        assertThat(actual.size(), is(entityCounts.stream().mapToInt(Integer::intValue).sum()));

        for (int i = 0; i < entityCounts.size(); i++) {
            for (int j = 0; j < entityCounts.get(i); j++) {
                assertThat(actual.containsKey(String.format(getEntityNameFormat(entityName), i, j)), is(true));
            }
        }
    }

    private List<KickoffRequest> getRequestWithEntities(List<Integer> counts, BiConsumer<? super KickoffRequest,
            String> action, String entityName) {
        List<KickoffRequest> kickoffRequests = new ArrayList<>();
        for (int i = 0; i < counts.size(); i++) {
            KickoffRequest kickoffRequest = getNormalImpactHumanPiRequestNoSamples();
            for (int j = 0; j < counts.get(i); j++) {
                action.accept(kickoffRequest, String.format(getEntityNameFormat(entityName), i, j));
            }
            kickoffRequests.add(kickoffRequest);
        }

        return kickoffRequests;
    }

    private String getEntityNameFormat(String entityName) {
        return "request_%d" + entityName + "%d";
    }

    private KickoffRequest getHumanPiRequest() {
        KickoffRequest kickoffRequest = getRequestWithPassedSample();
        kickoffRequest.setSpecies(RequestSpecies.HUMAN);
        kickoffRequest.setPi("King Julian");

        return kickoffRequest;
    }

    private KickoffRequest getNormalImpactHumanPiRequest() {
        KickoffRequest kickoffRequest = getNormalImpactHumanRequest();
        kickoffRequest.setPi("King Julian");
        return kickoffRequest;
    }

    private KickoffRequest getNormalImpactPiRequest() {
        KickoffRequest kickoffRequest = getNormalImpactRequest();

        kickoffRequest.setPi("King Julian");

        return kickoffRequest;
    }

    private KickoffRequest getNormalImpactHumanPiRequestNoSamples() {
        KickoffRequest normalImpactHumanPiRequest = getNormalImpactHumanPiRequest();
        normalImpactHumanPiRequest.getSamples().clear();
        return normalImpactHumanPiRequest;
    }

    private KickoffRequest getNormalImpactHumanRequest() {
        KickoffRequest kickoffRequest = getNormalImpactRequest();
        kickoffRequest.setSpeciesSet(getSpeciesSet(RequestSpecies.HUMAN));
        return kickoffRequest;
    }

    private KickoffRequest getNormalImpactRequest() {
        KickoffRequest kickoffRequest = getRequestWithPassedSample();
        kickoffRequest.setRequestType(RequestType.IMPACT);
        return kickoffRequest;
    }

    private KickoffRequest getEmptyRequest() {
        KickoffRequest kickoffRequest = new KickoffRequest(String.format("request_%d", id++), normalProcessingType);
        kickoffRequest.setSpeciesSet(getSpeciesSet(DEFAULT_SPECIES));
        return kickoffRequest;
    }

    private KickoffRequest addPassedSample(KickoffRequest kickoffRequest) {
        Sample sample = new Sample("sample1");
        Run run = new Run("run1");
        run.setSampleLevelQcStatus(QcStatus.PASSED);
        run.setPostQcStatus(QcStatus.PASSED);
        sample.putRunIfAbsent(run);
        kickoffRequest.putSampleIfAbsent(sample);

        return kickoffRequest;
    }

    private KickoffRequest getRequestWithPassedSample() {
        KickoffRequest kickoffRequest = getEmptyRequest();
        return addPassedSample(kickoffRequest);
    }

    private KickoffRequest getForcedImpactRequest() {
        KickoffRequest kickoffRequest = getNormalImpactHumanPiRequest();
        kickoffRequest.setProcessingType(new ForcedProcessingType());
        return kickoffRequest;
    }
}