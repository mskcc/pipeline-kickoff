package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.retriever.RequestsRetriever;
import org.mskcc.kickoff.sampleset.SampleSetRetriever;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;

public class SampleSetRequestRetriever implements RequestsRetriever {
    private final RequestDataPropagator requestDataPropagator;
    private final SampleSetToRequestConverter sampleSetToRequestConverter;
    private final SampleSetRetriever sampleSetRetriever;
    private final DataRecord sampleSetRecord;
    private final VeloxPairingsRetriever veloxPairingsRetriever;

    public SampleSetRequestRetriever(
            RequestDataPropagator requestDataPropagator,
            SampleSetToRequestConverter sampleSetToRequestConverter,
            SampleSetRetriever sampleSetRetriever,
            DataRecord sampleSetRecord,
            VeloxPairingsRetriever veloxPairingsRetriever) {
        this.requestDataPropagator = requestDataPropagator;
        this.sampleSetToRequestConverter = sampleSetToRequestConverter;
        this.sampleSetRetriever = sampleSetRetriever;
        this.sampleSetRecord = sampleSetRecord;
        this.veloxPairingsRetriever = veloxPairingsRetriever;
    }

    @Override
    public KickoffRequest retrieve(String projectId, ProcessingType processingType) throws Exception {
        KickoffSampleSet sampleSet = sampleSetRetriever.retrieve(projectId, processingType);

        requestDataPropagator.propagateRequestData(sampleSet.getKickoffRequests());
        KickoffRequest kickoffRequest = sampleSetToRequestConverter.convert(sampleSet);
        kickoffRequest.setPairingInfos(veloxPairingsRetriever.retrieve(sampleSetRecord, kickoffRequest));

        return kickoffRequest;
    }
}
