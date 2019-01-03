package org.mskcc.kickoff.poolednormals;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.NimblegenResolver;
import org.mskcc.kickoff.velox.Sample2DataRecordMap;

import static org.mockito.Mockito.mock;

public class PooledNormalsRetrieverFactoryTest {
    private PooledNormalsRetrieverFactory factory = new PooledNormalsRetrieverFactory(mock(NimblegenResolver.class),
            mock(Sample2DataRecordMap.class));
    private KickoffRequest kickoffRequest;

    @Before
    public void setUp() throws Exception {
        kickoffRequest = new KickoffRequest("321", mock(ProcessingType.class));
    }

    @Test
    public void whenRequestIsImpact_shouldReturnImpactExomeRetriever() throws Exception {
        //given
        kickoffRequest.setRequestType(RequestType.IMPACT);

        //when
        PooledNormalsRetriever retriever = factory.getPooledNormalsRetriever(kickoffRequest);

        //then
        Assertions.assertThat(retriever).isInstanceOf(ImpactExomePooledNormalsRetriever.class);
    }

    @Test
    public void whenRequestIsExome_shouldReturnImpactExomeRetriever() throws Exception {
        //given
        kickoffRequest.setRequestType(RequestType.IMPACT);

        //when
        PooledNormalsRetriever retriever = factory.getPooledNormalsRetriever(kickoffRequest);

        //then
        Assertions.assertThat(retriever).isInstanceOf(ImpactExomePooledNormalsRetriever.class);
    }

    @Test
    public void whenRequestIsRNASeq_shouldReturnRNASeqRetriever() throws Exception {
        //given
        kickoffRequest.setRequestType(RequestType.RNASEQ);

        //when
        PooledNormalsRetriever retriever = factory.getPooledNormalsRetriever(kickoffRequest);

        //then
        Assertions.assertThat(retriever).isInstanceOf(RNASeqPooledNormalsRetriever.class);
    }

    @Test
    public void whenRequestIsOther_shouldReturnRNASeqRetriever() throws Exception {
        //given
        kickoffRequest.setRequestType(RequestType.OTHER);

        //when
        PooledNormalsRetriever retriever = factory.getPooledNormalsRetriever(kickoffRequest);

        //then
        Assertions.assertThat(retriever).isInstanceOf(RNASeqPooledNormalsRetriever.class);
    }

}