package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.converter.SampleSetToRequestConverter;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.SampleSet;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.RequestDataPropagator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SampleSetRequestRetrieverTest {
    private final ProcessingType normalProcessingType = new NormalProcessingType(mock(ProjectFilesArchiver.class));
    private final RequestDataPropagator requetsDataPropagator = mock(RequestDataPropagator.class);
    private final SampleSetToRequestConverter sampleSetToReqConv = mock(SampleSetToRequestConverter.class);
    private final SampleSetRetriever sampleSetRetriever = mock(SampleSetRetriever.class);
    private final String projId = "12345_P";
    private SampleSetRequestRetriever sampleSetRequestRetriever;

    @Before
    public void setUp() throws Exception {
        sampleSetRequestRetriever = new SampleSetRequestRetriever(requetsDataPropagator, sampleSetToReqConv, sampleSetRetriever, mock(DataRecord.class), mock(VeloxPairingsRetriever.class));
    }

    @Test
    public void whenSampleSetRequestIsRetrieved_shouldReturnKickoffRequest() throws Exception {
        //given
        SampleSet sampleSet = getSampleSet();
        when(sampleSetRetriever.retrieve(projId, normalProcessingType)).thenReturn(sampleSet);
        KickoffRequest kickoffReq = getKickoffReq();
        when(sampleSetToReqConv.convert(sampleSet)).thenReturn(kickoffReq);

        //when
        KickoffRequest request = sampleSetRequestRetriever.retrieve(projId, normalProcessingType);

        //then
        assertThat(request, is(kickoffReq));
    }

    private SampleSet getSampleSet() {
        SampleSet sampleSet = new SampleSet("sampleSetId");

        return sampleSet;
    }

    public KickoffRequest getKickoffReq() {
        KickoffRequest kickoffRequest = new KickoffRequest(projId, normalProcessingType);
        return kickoffRequest;
    }
}