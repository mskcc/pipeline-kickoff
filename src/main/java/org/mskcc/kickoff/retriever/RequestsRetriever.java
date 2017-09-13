package org.mskcc.kickoff.retriever;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;

public interface RequestsRetriever {
    KickoffRequest retrieve(String projectId, ProcessingType processingType) throws Exception;
}
