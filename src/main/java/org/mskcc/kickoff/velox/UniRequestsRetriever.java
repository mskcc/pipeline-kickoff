package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.poolednormals.PooledNormalsRetrieverFactory;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.*;
import org.mskcc.kickoff.validator.ErrorRepository;

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
    private ErrorRepository errorRepository;

    public UniRequestsRetriever(User user,
                                DataRecordManager dataRecordManager,
                                ProjectInfoRetriever projectInfoRetriever,
                                RequestDataPropagator requestDataPropagator,
                                NimblegenResolver nimblegenResolver,
                                Sample2DataRecordMap sample2DataRecordMap,
                                VeloxPairingsRetriever veloxPairingsRetriever,
                                BiPredicate<Sample, Sample> pairingValidPredicate,
                                ReadOnlyExternalSamplesRepository externalSamplesRepository,
                                ErrorRepository errorRepository,
                                String fastqDir) {
        this.user = user;
        this.dataRecordManager = dataRecordManager;
        this.requestDataPropagator = requestDataPropagator;
        this.veloxPairingsRetriever = veloxPairingsRetriever;
        this.singleRequestRetriever = new VeloxSingleRequestRetriever(user, dataRecordManager, new
                RequestTypeResolver(), projectInfoRetriever,
                new PooledNormalsRetrieverFactory(nimblegenResolver, sample2DataRecordMap), nimblegenResolver,
                sample2DataRecordMap, externalSamplesRepository, errorRepository, fastqDir);
        this.pairingValidPredicate = pairingValidPredicate;
        this.errorRepository = errorRepository;
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
