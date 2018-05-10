package org.mskcc.kickoff.sampleset;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SamplesToRequestsConverter {
    private SingleRequestRetriever singleRequestRetriever;

    public SamplesToRequestsConverter(SingleRequestRetriever singleRequestRetriever) {
        this.singleRequestRetriever = singleRequestRetriever;
    }

    public Map<String, KickoffRequest> convert(Collection<Sample> samples, ProcessingType processingType) throws
            Exception {
        Map<String, KickoffRequest> requests = new HashMap<>();
        List<String> sampleIds = getSampleIds(samples);
        for (Sample sample : samples) {
            String requestId = sample.getRequestId();
            if (!requests.containsKey(requestId)) {
                KickoffRequest kickoffRequest = singleRequestRetriever.retrieve(requestId, sampleIds, processingType);
                requests.put(requestId, kickoffRequest);
            }
        }

        return requests;
    }

    private List<String> getSampleIds(Collection<Sample> samples) {
        return samples.stream().map(s -> s.getIgoId()).collect(Collectors.toList());
    }
}
