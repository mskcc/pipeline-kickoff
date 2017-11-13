package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.mskcc.kickoff.converter.SampleSetToRequestConverter;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.retriever.RequestNotFoundException;
import org.mskcc.kickoff.retriever.RequestsRetriever;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;

import java.util.List;
import java.util.function.Predicate;

import static org.mskcc.util.VeloxConstants.SAMPLE_SET;

public class RequestsRetrieverFactory {
    private final Predicate<String> sampleSetProjectPredicate;
    private ProjectInfoRetriever projectInfoRetriever;
    private RequestDataPropagator requestDataPropagator;
    private SampleSetToRequestConverter sampleSetToRequestConverter;

    public RequestsRetrieverFactory(ProjectInfoRetriever projectInfoRetriever,
                                    RequestDataPropagator requestDataPropagator,
                                    SampleSetToRequestConverter sampleSetToRequestConverter) {
        this.projectInfoRetriever = projectInfoRetriever;
        this.requestDataPropagator = requestDataPropagator;
        this.sampleSetToRequestConverter = sampleSetToRequestConverter;
        this.sampleSetProjectPredicate = new SampleSetProjectPredicate();
    }

    public RequestsRetriever getRequestsRetriever(User user, DataRecordManager dataRecordManager, String projectId)
            throws RequestNotFoundException {
        VeloxPairingsRetriever veloxPairingsRetriever = new VeloxPairingsRetriever(user);

        if (sampleSetProjectPredicate.test(projectId))
            return getSampleSetRequestsRetriever(user, dataRecordManager, projectId, veloxPairingsRetriever);

        return new UniRequestsRetriever(user, dataRecordManager, projectInfoRetriever, requestDataPropagator,
                veloxPairingsRetriever);
    }

    private RequestsRetriever getSampleSetRequestsRetriever(User user, DataRecordManager dataRecordManager, String
            projectId, VeloxPairingsRetriever veloxPairingsRetriever) {
        SingleRequestRetriever requestsRetriever = new VeloxSingleRequestRetriever(user, dataRecordManager,
                projectInfoRetriever);
        DataRecord sampleSetRecord = getSampleSetRecord(projectId, dataRecordManager, user);
        SampleSetProxy veloxSampleSetProxy = new VeloxSampleSetProxy(sampleSetRecord, user, requestsRetriever);

        SamplesToRequestsConverter samplesToRequestsConverter = new SamplesToRequestsConverter(new
                VeloxSingleRequestRetriever(user, dataRecordManager, projectInfoRetriever));
        SampleSetRetriever sampleSetRetriever = new SampleSetRetriever(veloxSampleSetProxy, samplesToRequestsConverter);

        return new SampleSetRequestRetriever(requestDataPropagator, sampleSetToRequestConverter, sampleSetRetriever,
                sampleSetRecord, veloxPairingsRetriever);
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
