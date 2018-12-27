package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.poolednormals.PooledNormalsRetrieverFactory;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.retriever.RequestNotFoundException;
import org.mskcc.kickoff.retriever.RequestsRetriever;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import static org.mskcc.util.VeloxConstants.REQUEST;

public class UniRequestsRetriever implements RequestsRetriever {
    private final DataRecordManager dataRecordManager;
    private final User user;
    private final SingleRequestRetriever singleRequestRetriever;
    private final RequestDataPropagator requestDataPropagator;
    private VeloxPairingsRetriever veloxPairingsRetriever;
    private BiPredicate<Sample, Sample> pairingValidPredicate;

    public UniRequestsRetriever(User user,
                                DataRecordManager dataRecordManager,
                                ProjectInfoRetriever projectInfoRetriever,
                                RequestDataPropagator requestDataPropagator,
                                VeloxPairingsRetriever veloxPairingsRetriever,
                                BiPredicate<Sample, Sample> pairingValidPredicate) {
        this.user = user;
        this.dataRecordManager = dataRecordManager;
        this.requestDataPropagator = requestDataPropagator;
        this.veloxPairingsRetriever = veloxPairingsRetriever;
        this.singleRequestRetriever = new VeloxSingleRequestRetriever(user, dataRecordManager, new
                RequestTypeResolver(), projectInfoRetriever,
                new PooledNormalsRetrieverFactory());
        this.pairingValidPredicate = pairingValidPredicate;
    }

    @Override
    public KickoffRequest retrieve(String requestId, ProcessingType processingType) throws Exception {
        List<DataRecord> requestsDataRecords = dataRecordManager.queryDataRecords(REQUEST, "RequestId = '" +
                requestId + "'", user);

        if (requestsDataRecords == null || requestsDataRecords.size() != 1)
            throw new RequestNotFoundException(String.format("Request with id: %s not found", requestId));

        KickoffRequest kickoffRequest = singleRequestRetriever.retrieve(requestId, processingType);
        requestDataPropagator.propagateRequestData(Arrays.asList(kickoffRequest));
        kickoffRequest.setRunNumbers(String.valueOf(kickoffRequest.getRunNumber()));
        kickoffRequest.addRequests(Arrays.asList(kickoffRequest));
        kickoffRequest.setPairingInfos(veloxPairingsRetriever.retrieve(requestsDataRecords.get(0), kickoffRequest,
                pairingValidPredicate));
        VeloxProjectProxy.resolvePairings(kickoffRequest);

        return kickoffRequest;
    }

}
