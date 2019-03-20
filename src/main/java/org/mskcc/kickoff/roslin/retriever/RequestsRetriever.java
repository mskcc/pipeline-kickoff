package org.mskcc.kickoff.roslin.retriever;

import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.roslin.process.ProcessingType;

public interface RequestsRetriever {
    KickoffRequest retrieve(String projectId, ProcessingType processingType) throws Exception;
}
