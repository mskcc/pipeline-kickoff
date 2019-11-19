package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.poolednormals.PooledNormalsRetrieverFactory;
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
    private NimblegenResolver nimblegenResolver;
    private Sample2DataRecordMap sample2DataRecordMap;

    public RequestsRetrieverFactory(ProjectInfoRetriever projectInfoRetriever,
                                    RequestDataPropagator sampleSetRequestDataPropagator,
                                    RequestDataPropagator singleRequestRequestDataPropagator,
                                    SampleSetToRequestConverter sampleSetToRequestConverter,
                                    ReadOnlyExternalSamplesRepository readOnlyExternalSamplesRepository,
                                    BiPredicate<Sample, Sample> sampleSetPairingValidPredicate,
                                    BiPredicate<Sample, Sample> singleRequestPairingValidPredicate,
                                    ErrorRepository errorRepository, NimblegenResolver nimblegenResolver,
                                    Sample2DataRecordMap sample2DataRecordMap) {
        this.projectInfoRetriever = projectInfoRetriever;
        this.sampleSetRequestDataPropagator = sampleSetRequestDataPropagator;
        this.singleRequestRequestDataPropagator = singleRequestRequestDataPropagator;
        this.sampleSetToRequestConverter = sampleSetToRequestConverter;
        this.nimblegenResolver = nimblegenResolver;
        this.sample2DataRecordMap = sample2DataRecordMap;
        this.sampleSetProjectPredicate = new SampleSetProjectPredicate();
        this.externalSamplesRepository = readOnlyExternalSamplesRepository;
        this.sampleSetPairingValidPredicate = sampleSetPairingValidPredicate;
        this.singleRequestPairingValidPredicate = singleRequestPairingValidPredicate;
        this.errorRepository = errorRepository;
    }

    public RequestsRetriever getRequestsRetriever(User user, DataRecordManager dataRecordManager, String projectId,
                                                  String fastqDir)
            throws RequestNotFoundException {
        VeloxPairingsRetriever veloxPairingsRetriever = new VeloxPairingsRetriever(user, errorRepository);

        if (sampleSetProjectPredicate.test(projectId))
            return getSampleSetRequestsRetriever(user, dataRecordManager, projectId, veloxPairingsRetriever, fastqDir);

        return new UniRequestsRetriever(user, dataRecordManager, projectInfoRetriever,
                singleRequestRequestDataPropagator,
                nimblegenResolver, sample2DataRecordMap, veloxPairingsRetriever, singleRequestPairingValidPredicate,
                externalSamplesRepository, errorRepository, fastqDir);
    }

    private RequestsRetriever getSampleSetRequestsRetriever(User user, DataRecordManager dataRecordManager, String
            projectId, VeloxPairingsRetriever veloxPairingsRetriever, String fastqDir) {
        RequestTypeResolver requestTypeResolver = new RequestTypeResolver();

        PooledNormalsRetrieverFactory pooledNormRetrFact = new PooledNormalsRetrieverFactory(nimblegenResolver,
                sample2DataRecordMap);
        SingleRequestRetriever requestsRetriever = new VeloxSingleRequestRetriever(user, dataRecordManager,
                requestTypeResolver, projectInfoRetriever, pooledNormRetrFact, nimblegenResolver,
                sample2DataRecordMap, externalSamplesRepository, errorRepository, fastqDir);

        DataRecord sampleSetRecord = getSampleSetRecord(projectId, dataRecordManager, user);

        SampleSetProxy veloxSampleSetProxy = new VeloxSampleSetProxy(sampleSetRecord, user, requestsRetriever,
                externalSamplesRepository);

        SamplesToRequestsConverter samplesToRequestsConverter = new SamplesToRequestsConverter(requestsRetriever);
        SampleSetRetriever sampleSetRetriever = new SampleSetRetriever(veloxSampleSetProxy, samplesToRequestsConverter);

        return new SampleSetRequestRetriever(sampleSetRequestDataPropagator, sampleSetToRequestConverter,
                sampleSetRetriever,
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
