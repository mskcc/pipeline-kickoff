package org.mskcc.kickoff.retriever;

import org.junit.Test;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.util.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class RequestDataPropagatorTest {
    private RequestDataPropagator requestDataPropagator = new RequestDataPropagator("", "");

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