package org.mskcc.kickoff.retriever;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.List;

public interface SingleRequestRetriever {
    KickoffRequest retrieve(String requestId, List<String> sampleIds, ProcessingType processingType) throws Exception;

    KickoffRequest retrieve(String requestId, ProcessingType processingType) throws Exception;
}
