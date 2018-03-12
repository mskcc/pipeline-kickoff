package org.mskcc.kickoff.velox;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.SampleSet;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SampleSetRetrieverTest {
    private final ProcessingType normalProcessingType = mock(NormalProcessingType.class);
    private final String projId = "1234_P";
    private final SingleRequestRetriever singleRequestRetriever = new SingleRequestRetrieverMock();
    private SampleSetRetriever sampleSetRetriever;
    private SampleSetProxy sampleSetProxyMock = mock(SampleSetProxy.class);
    private SamplesToRequestsConverter samplesToReqConv = new SamplesToRequestsConverter(singleRequestRetriever);

    @Before
    public void setUp() throws Exception {
        sampleSetRetriever = new SampleSetRetriever(sampleSetProxyMock, samplesToReqConv);
    }

    @Test
    public void whenSampleSetHasNoSamplesNorRequests_shouldReturnSampleSetWithEmptyRequests() throws Exception {
        //given
        String baitVer = "someBait";
        String primaryReqId = "primaryId";
        Recipe recipe = Recipe.SMARTER_AMP_SEQ;

        when(sampleSetProxyMock.getBaitVersion()).thenReturn(baitVer);
        when(sampleSetProxyMock.getPrimaryRequestId()).thenReturn(primaryReqId);
        when(sampleSetProxyMock.getRecipe()).thenReturn(recipe.getValue());

        //when
        SampleSet sampleSet = sampleSetRetriever.retrieve(projId, normalProcessingType);

        //then
        assertThat(sampleSet.getBaitSet(), is(baitVer));
        assertThat(sampleSet.getRequests().size(), is(0));
        assertThat(sampleSet.getName(), is(projId));
        assertThat(sampleSet.getPrimaryRequestId(), is(primaryReqId));
        assertThat(sampleSet.getRecipe(), is(recipe));
    }

    @Test
    public void whenSampleSetHasOneRequest_shouldReturnSampleSetWithThisOneRequest() throws Exception {
        List<KickoffRequest> requests = getKickoffRequests(1);

        when(sampleSetProxyMock.getRequests(normalProcessingType)).thenReturn(requests);
        when(sampleSetProxyMock.getRecipe()).thenReturn(Recipe.AMPLI_SEQ.getValue());

        //when
        SampleSet sampleSet = sampleSetRetriever.retrieve(projId, normalProcessingType);

        //then
        assertContainsAllRequests(requests, sampleSet);
    }

    @Test
    public void whenSampleSetHasMultipleRequests_shouldReturnSampleSetWithAllThoseRequest() throws Exception {
        List<KickoffRequest> requests = getKickoffRequests(3);
        when(sampleSetProxyMock.getRequests(normalProcessingType)).thenReturn(requests);
        when(sampleSetProxyMock.getRecipe()).thenReturn(Recipe.AMPLI_SEQ.getValue());

        //when
        SampleSet sampleSet = sampleSetRetriever.retrieve(projId, normalProcessingType);

        //then
        assertContainsAllRequests(requests, sampleSet);
    }

    @Test
    public void whenSampleSetHasSamplesFromOneRequest_shouldReturnSampleSetWithThisOneRequests() throws Exception {
        List<Sample> samples = getSamples(1);

        when(sampleSetProxyMock.getSamples()).thenReturn(samples);
        when(sampleSetProxyMock.getRecipe()).thenReturn(Recipe.AMPLI_SEQ.getValue());

        //when
        SampleSet sampleSet = sampleSetRetriever.retrieve(projId, normalProcessingType);

        //then
        assertContainsAllSamplesRequests(samples, sampleSet);
    }

    @Test
    public void whenSampleSetHasSamplesFromMultipleRequests_shouldReturnSampleSetWithAllThoseRequests() throws
            Exception {
        List<Sample> samples = getSamples(1, 2, 3, 4);

        when(sampleSetProxyMock.getSamples()).thenReturn(samples);
        when(sampleSetProxyMock.getRecipe()).thenReturn(Recipe.AMPLI_SEQ.getValue());

        //when
        SampleSet sampleSet = sampleSetRetriever.retrieve(projId, normalProcessingType);

        //then
        assertContainsAllSamplesRequests(samples, sampleSet);
    }

    @Test
    public void
    whenSampleSetHasSamplesFromMultipleRequestsAndMultipleRequests_shouldReturnSampleSetWithAllThoseRequests() throws
            Exception {
        List<Sample> samples = getSamples(1, 2, 3, 4);
        List<KickoffRequest> requests = getKickoffRequests(3);

        when(sampleSetProxyMock.getSamples()).thenReturn(samples);
        when(sampleSetProxyMock.getRequests(normalProcessingType)).thenReturn(requests);
        when(sampleSetProxyMock.getRecipe()).thenReturn(Recipe.AMPLI_SEQ.getValue());

        //when
        SampleSet sampleSet = sampleSetRetriever.retrieve(projId, normalProcessingType);

        //then
        assertContainsAllSamplesRequests(samples, requests, sampleSet);
    }

    private void assertContainsAllSamplesRequests(List<Sample> samples, List<KickoffRequest> requests, SampleSet
            sampleSet) throws Exception {
        Map<String, KickoffRequest> sampleRequests = getRequestsFromSamples(samples);

        Set<KickoffRequest> uniqueRequests = new HashSet<>(requests);
        uniqueRequests.addAll(sampleRequests.values());

        assertContainsAllRequests(new ArrayList<>(uniqueRequests), sampleSet);
    }

    private void assertContainsAllSamplesRequests(List<Sample> samples, SampleSet sampleSet) throws Exception {
        Map<String, KickoffRequest> requests = getRequestsFromSamples(samples);
        assertThat(sampleSet.getRequests().size(), is(requests.size()));

        for (KickoffRequest request : requests.values()) {
            assertThat(sampleSet.getRequests().contains(request), is(true));
        }
    }

    private Map<String, KickoffRequest> getRequestsFromSamples(List<Sample> samples) throws Exception {

        return samplesToReqConv.convert(samples, normalProcessingType);
    }

    private List<Sample> getSamples(int... numberOfSamples) {
        List<Sample> samples = new ArrayList<>();

        for (int i = 0; i < numberOfSamples.length; i++) {
            for (int j = 0; j < numberOfSamples[i]; j++) {
                Sample sample = new Sample(String.valueOf(j));
                sample.setRequestId(String.valueOf(i));
                samples.add(sample);
            }
        }

        return samples;
    }

    private List<KickoffRequest> getKickoffRequests(int numberOfRequests) {
        List<KickoffRequest> requests = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {
            KickoffRequest request = new KickoffRequest(String.valueOf(i), normalProcessingType);
            requests.add(request);
        }

        return requests;
    }

    private void assertContainsAllRequests(List<KickoffRequest> requests, SampleSet sampleSet) {
        assertThat(sampleSet.getRequests().size(), is(requests.size()));

        for (KickoffRequest request : requests) {
            assertThat(sampleSet.getRequests().contains(request), is(true));
        }
    }

    private class SingleRequestRetrieverMock implements SingleRequestRetriever {
        @Override
        public KickoffRequest retrieve(String requestId, List<String> sampleIds, ProcessingType processingType)
                throws Exception {
            return new KickoffRequest(requestId, processingType);
        }

        @Override
        public KickoffRequest retrieve(String requestId, ProcessingType processingType) throws Exception {
            return new KickoffRequest(requestId, processingType);
        }
    }
}