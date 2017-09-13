package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.user.User;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.retriever.SingleRequestRetriever;
import org.mskcc.util.VeloxConstants;

import java.util.*;

import static org.mskcc.util.VeloxConstants.*;

public class VeloxSampleSetProxy implements SampleSetProxy {
    private DataRecord sampleSetRecord;
    private User user;
    private SingleRequestRetriever singleRequestRetriever;

    public VeloxSampleSetProxy(DataRecord sampleSetRecord, User user, SingleRequestRetriever singleRequestRetriever) {
        this.sampleSetRecord = sampleSetRecord;
        this.user = user;
        this.singleRequestRetriever = singleRequestRetriever;
    }

    @Override
    public String getRecipe() throws Exception {
        return sampleSetRecord.getStringVal(VeloxConstants.RECIPE, user);
    }

    @Override
    public String getBaitVersion() throws Exception {
        return sampleSetRecord.getStringVal(VeloxConstants.BAIT_SET, user);
    }

    @Override
    public String getPrimaryRequestId() throws Exception {
        return sampleSetRecord.getStringVal(PRIME_REQUEST, user);
    }

    @Override
    public Collection<KickoffRequest> getRequests(ProcessingType processingType) throws Exception {
        List<KickoffRequest> kickoffRequests = new ArrayList<>();

        List<DataRecord> requestsDataRecords = Arrays.asList(sampleSetRecord.getChildrenOfType(REQUEST, user));
        for (DataRecord requestsDataRecord : requestsDataRecords) {
            String requestId = requestsDataRecord.getStringVal(REQUEST_ID, user);
            KickoffRequest kickoffRequest = singleRequestRetriever.retrieve(requestId, processingType);
            kickoffRequests.add(kickoffRequest);
        }

        return kickoffRequests;
    }

    @Override
    public Collection<Sample> getSamples() throws Exception {
        List<DataRecord> sampleRecords = new LinkedList<>(Arrays.asList(sampleSetRecord.getChildrenOfType(SAMPLE, user)));

        List<Sample> samples = new LinkedList<>();

        for (DataRecord sampleRecord : sampleRecords) {
            String sampleId = sampleRecord.getStringVal(SAMPLE_ID, user);
            Sample sample = new Sample(sampleId);
            String requestId = sampleRecord.getStringVal(REQUEST_ID, user);
            sample.setRequestId(requestId);

            samples.add(sample);
        }

        return samples;
    }
}
