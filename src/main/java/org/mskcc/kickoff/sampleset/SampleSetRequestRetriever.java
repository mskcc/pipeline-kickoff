package org.mskcc.kickoff.sampleset;

import com.velox.api.datarecord.DataRecord;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.retriever.RequestsRetriever;
import org.mskcc.kickoff.velox.VeloxPairingsRetriever;
import org.mskcc.kickoff.velox.VeloxProjectProxy;

import java.util.function.BiPredicate;

public class SampleSetRequestRetriever implements RequestsRetriever {
    private final RequestDataPropagator requestDataPropagator;
    private final SampleSetToRequestConverter sampleSetToRequestConverter;
    private final SampleSetRetriever sampleSetRetriever;
    private final DataRecord sampleSetRecord;
    private final VeloxPairingsRetriever veloxPairingsRetriever;
    private final BiPredicate<Sample, Sample> pairingValidPredicate;

    public SampleSetRequestRetriever(
            RequestDataPropagator requestDataPropagator,
            SampleSetToRequestConverter sampleSetToRequestConverter,
            SampleSetRetriever sampleSetRetriever,
            DataRecord sampleSetRecord,
            VeloxPairingsRetriever veloxPairingsRetriever,
            BiPredicate<Sample, Sample> pairingValidPredicate) {
        this.requestDataPropagator = requestDataPropagator;
        this.sampleSetToRequestConverter = sampleSetToRequestConverter;
        this.sampleSetRetriever = sampleSetRetriever;
        this.sampleSetRecord = sampleSetRecord;
        this.veloxPairingsRetriever = veloxPairingsRetriever;
        this.pairingValidPredicate = pairingValidPredicate;
    }

    @Override
    public KickoffRequest retrieve(String projectId, ProcessingType processingType) throws Exception {
        KickoffSampleSet sampleSet = sampleSetRetriever.retrieve(projectId, processingType);

        requestDataPropagator.propagateRequestData(sampleSet.getKickoffRequests());
        KickoffRequest kickoffRequest = sampleSetToRequestConverter.convert(sampleSet);
        kickoffRequest.setPairingInfos(veloxPairingsRetriever.retrieve(sampleSetRecord, kickoffRequest,
                pairingValidPredicate));
        VeloxProjectProxy.resolvePairings(kickoffRequest);

        return kickoffRequest;
    }
}
