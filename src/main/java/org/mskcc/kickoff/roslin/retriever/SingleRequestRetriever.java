package org.mskcc.kickoff.roslin.retriever;

import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.roslin.process.ProcessingType;

import java.util.List;

public interface SingleRequestRetriever {
    KickoffRequest retrieve(String requestId, List<String> sampleIds, ProcessingType processingType) throws Exception;

    KickoffRequest retrieve(String requestId, ProcessingType processingType) throws Exception;
}
