package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import org.mskcc.kickoff.converter.SampleSetToRequestConverter;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.SampleSet;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.retriever.RequestsRetriever;

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
        SampleSet sampleSet = sampleSetRetriever.retrieve(projectId, processingType);

        requestDataPropagator.propagateRequestData(sampleSet.getRequests());
        KickoffRequest kickoffRequest = sampleSetToRequestConverter.convert(sampleSet);
        kickoffRequest.setPairingInfos(veloxPairingsRetriever.retrieve(sampleSetRecord, kickoffRequest));

        return kickoffRequest;
    }
}
