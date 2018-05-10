package org.mskcc.kickoff.sampleset;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SamplesToRequestsConverterTest {
    public static final String REQUEST_ID_1 = "12345_A";
    public static final String REQUEST_ID_2 = "12345_B";
    public static final String REQUEST_ID_3 = "12345_C";
    private SamplesToRequestsConverter samplesToRequestsConverter;
    private ProcessingType procType = mock(ProcessingType.class);

    @Before
    public void setUp() throws Exception {
        SingleRequestRetriever singleReqRetriever = mock(SingleRequestRetriever.class);
        when(singleReqRetriever.retrieve(eq(REQUEST_ID_1), any(), eq(procType))).thenReturn(new KickoffRequest
                (REQUEST_ID_1, procType));
        when(singleReqRetriever.retrieve(eq(REQUEST_ID_2), any(), eq(procType))).thenReturn(new KickoffRequest
                (REQUEST_ID_2, procType));
        when(singleReqRetriever.retrieve(eq(REQUEST_ID_3), any(), eq(procType))).thenReturn(new KickoffRequest
                (REQUEST_ID_3, procType));
        samplesToRequestsConverter = new SamplesToRequestsConverter(singleReqRetriever);
    }

    @Test
    public void whenSamplesList_shouldReturnListOfUniqueRequests() throws Exception {
        assertRequests(Arrays.asList(getSample("1", REQUEST_ID_1)), Arrays.asList(REQUEST_ID_1));

        assertRequests(
                Arrays.asList(
                        getSample("1", REQUEST_ID_1),
                        getSample("2", REQUEST_ID_1)),
                Arrays.asList(REQUEST_ID_1));

        assertRequests(
                Arrays.asList(
                        getSample("1", REQUEST_ID_1),
                        getSample("2", REQUEST_ID_2),
                        getSample("3", REQUEST_ID_3)
                ),
                Arrays.asList(
                        REQUEST_ID_1,
                        REQUEST_ID_2,
                        REQUEST_ID_3
                ));

        assertRequests(
                Arrays.asList(
                        getSample("1", REQUEST_ID_1),
                        getSample("2", REQUEST_ID_1),
                        getSample("3", REQUEST_ID_2),
                        getSample("4", REQUEST_ID_2),
                        getSample("5", REQUEST_ID_2)
                ),
                Arrays.asList(
                        REQUEST_ID_1,
                        REQUEST_ID_2
                ));
    }

    private void assertRequests(List<Sample> samples, List<String> reqIds) throws Exception {
        Map<String, KickoffRequest> requests = samplesToRequestsConverter.convert(samples, procType);

        assertThat(requests.size(), is(reqIds.size()));
        for (String reqId : reqIds) {
            assertThat(requests.containsKey(reqId), is(true));
        }
    }

    private Sample getSample(String igoId, String reqId) {
        Sample sample = new Sample(igoId);
        sample.setRequestId(reqId);

        return sample;
    }
}