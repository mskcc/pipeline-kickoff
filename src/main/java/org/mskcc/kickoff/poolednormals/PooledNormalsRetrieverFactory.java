package org.mskcc.kickoff.poolednormals;

import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.retriever.NimblegenResolver;
import org.mskcc.kickoff.velox.Sample2DataRecordMap;

public class PooledNormalsRetrieverFactory {
    private NimblegenResolver nimblegenResolver;
    private Sample2DataRecordMap sample2DataRecordMap;

    public PooledNormalsRetrieverFactory(NimblegenResolver nimblegenResolver, Sample2DataRecordMap
            sample2DataRecordMap) {
        this.nimblegenResolver = nimblegenResolver;
        this.sample2DataRecordMap = sample2DataRecordMap;
    }

    public PooledNormalsRetriever getPooledNormalsRetriever(KickoffRequest kickoffRequest) {
        RequestType requestType = kickoffRequest.getRequestType();

        if (requestType == RequestType.EXOME || requestType == RequestType.IMPACT)
            return new ImpactExomePooledNormalsRetriever(nimblegenResolver, sample2DataRecordMap);
        return new RNASeqPooledNormalsRetriever();
    }
}
