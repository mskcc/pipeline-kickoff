package org.mskcc.kickoff.poolednormals;

import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;

public class PooledNormalsRetrieverFactory {
    public PooledNormalsRetriever getPooledNormalsRetriever(KickoffRequest kickoffRequest) {
        RequestType requestType = kickoffRequest.getRequestType();

        if (requestType == RequestType.EXOME || requestType == RequestType.IMPACT)
            return new ImpactExomePooledNormalsRetriever();
        return new RNASeqPooledNormalsRetriever();
    }
}
