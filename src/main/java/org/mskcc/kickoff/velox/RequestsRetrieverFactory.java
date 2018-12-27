package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.retriever.*;
import org.mskcc.kickoff.sampleset.*;
import org.mskcc.kickoff.validator.ErrorRepository;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static org.mskcc.util.VeloxConstants.SAMPLE_SET;

public class RequestsRetrieverFactory {
    private final Predicate<String> sampleSetProjectPredicate;
    private ProjectInfoRetriever projectInfoRetriever;
    private RequestDataPropagator sampleSetRequestDataPropagator;
    private RequestDataPropagator singleRequestRequestDataPropagator;
    private SampleSetToRequestConverter sampleSetToRequestConverter;
    private ReadOnlyExternalSamplesRepository externalSamplesRepository;
    private BiPredicate<Sample, Sample> sampleSetPairingValidPredicate;
    private BiPredicate<Sample, Sample> singleRequestPairingValidPredicate;
    private ErrorRepository errorRepository;

    public RequestsRetrieverFactory(ProjectInfoRetriever projectInfoRetriever,
                                    RequestDataPropagator sampleSetRequestDataPropagator,
                                    RequestDataPropagator singleRequestRequestDataPropagator,
                                    SampleSetToRequestConverter sampleSetToRequestConverter,
                                    ReadOnlyExternalSamplesRepository readOnlyExternalSamplesRepository,
                                    BiPredicate<Sample, Sample> sampleSetPairingValidPredicate,
                                    BiPredicate<Sample, Sample> singleRequestPairingValidPredicate,
                                    ErrorRepository errorRepository) {
        this.projectInfoRetriever = projectInfoRetriever;
        this.sampleSetRequestDataPropagator = sampleSetRequestDataPropagator;
        this.singleRequestRequestDataPropagator = singleRequestRequestDataPropagator;
        this.sampleSetToRequestConverter = sampleSetToRequestConverter;
        this.sampleSetProjectPredicate = new SampleSetProjectPredicate();
        this.externalSamplesRepository = readOnlyExternalSamplesRepository;
        this.sampleSetPairingValidPredicate = sampleSetPairingValidPredicate;
        this.singleRequestPairingValidPredicate = singleRequestPairingValidPredicate;
        this.errorRepository = errorRepository;
    }

    public RequestsRetriever getRequestsRetriever(User user, DataRecordManager dataRecordManager, String projectId)
            throws RequestNotFoundException {
        VeloxPairingsRetriever veloxPairingsRetriever = new VeloxPairingsRetriever(user, errorRepository);

        if (sampleSetProjectPredicate.test(projectId))
            return getSampleSetRequestsRetriever(user, dataRecordManager, projectId, veloxPairingsRetriever);

        return new UniRequestsRetriever(user, dataRecordManager, projectInfoRetriever, singleRequestRequestDataPropagator,
                veloxPairingsRetriever, singleRequestPairingValidPredicate);
    }

    private RequestsRetriever getSampleSetRequestsRetriever(User user, DataRecordManager dataRecordManager, String
            projectId, VeloxPairingsRetriever veloxPairingsRetriever) {
        RequestTypeResolver requestTypeResolver = new RequestTypeResolver();
        PooledNormalsRetriever pooledNormalsRetriever = new PooledNormalsRetriever();

        SingleRequestRetriever requestsRetriever = new VeloxSingleRequestRetriever(user, dataRecordManager,
                requestTypeResolver, projectInfoRetriever, pooledNormalsRetriever);

        DataRecord sampleSetRecord = getSampleSetRecord(projectId, dataRecordManager, user);

        SampleSetProxy veloxSampleSetProxy = new VeloxSampleSetProxy(sampleSetRecord, user, requestsRetriever,
                externalSamplesRepository);

        SamplesToRequestsConverter samplesToRequestsConverter = new SamplesToRequestsConverter(new
                VeloxSingleRequestRetriever(user, dataRecordManager, requestTypeResolver, projectInfoRetriever,
                pooledNormalsRetriever));
        SampleSetRetriever sampleSetRetriever = new SampleSetRetriever(veloxSampleSetProxy, samplesToRequestsConverter);

        return new SampleSetRequestRetriever(sampleSetRequestDataPropagator, sampleSetToRequestConverter, sampleSetRetriever,
                sampleSetRecord, veloxPairingsRetriever, sampleSetPairingValidPredicate);
    }

    private DataRecord getSampleSetRecord(String projectId, DataRecordManager dataRecordManager, User user) throws
            RequestNotFoundException {
        List<DataRecord> sampleSets;
        try {
            sampleSets = dataRecordManager.queryDataRecords(SAMPLE_SET, "Name = '" + projectId + "'", user);
        } catch (Exception e) {
            throw new RequestNotFoundException(String.format("Sample set: %s doesn't exist", projectId));
        }

        validateSampleSetExists(projectId, sampleSets);

        return sampleSets.get(0);
    }

    private void validateSampleSetExists(String projectId, List<DataRecord> sampleSets) throws
            RequestNotFoundException {
        if (sampleSets == null || sampleSets.size() == 0)
            throw new RequestNotFoundException(String.format("Sample set: %s doesn't exist", projectId));
    }

}
