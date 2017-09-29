package org.mskcc.kickoff.sampleset;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxConnectionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.object.IsCompatibleType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.Recipe;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.converter.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.converter.SampleSetToRequestConverter;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.mskcc.util.TestUtils;
import org.mskcc.util.VeloxConstants;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class SampleSetIntegrationTest {
    private static final Log LOG = LogFactory.getLog(SampleSetIntegrationTest.class);
    private static String connectionFile = "src/integration-test/resources/Connection-dev.txt";
    private static String connectionFileTest = "src/integration-test/resources/Connection-test.txt";
    private final String validSampleSetId = "set_1234";
    private final int recordId_02756_b = 278777;
    private final String reqId_02756_b = "02756_B";
    private final String reqId_04252_J = "04252_J";
    private final int sampleRecordId_02756_b_1 = 278822;
    private String designFilePath = "/ifs/projects/CMO/targets/designs";
    private String resultsPathPrefix = "/ifs/solres/seq";
    private ProjectFilesArchiver archiverMock = mock(ProjectFilesArchiver.class);
    private ProjectInfoRetriever projInfoRetriever = new ProjectInfoRetriever();
    private RequestDataPropagator reqDataPropagator = new RequestDataPropagator(designFilePath, resultsPathPrefix);
    private SampleSetProjectInfoConverter projInfoConv = new SampleSetProjectInfoConverter();
    private SampleSetToRequestConverter sampleSetToReqConv = new SampleSetToRequestConverter(projInfoConv);
    private RequestsRetrieverFactory requestsRetrieverFactory = new RequestsRetrieverFactory(projInfoRetriever, reqDataPropagator, sampleSetToReqConv);
    private VeloxProjectProxy veloxProjectProxy = new VeloxProjectProxy(connectionFile, archiverMock, requestsRetrieverFactory);
    private DataRecord sampleSetRecord;
    private VeloxConnection connection;
    private DataRecordManager dataRecordManager;
    private User user;
    private String request_05667_AB = "05667_AB";
    private String request_05667_AT = "05667_AT";

    @Before
    public void setUp() throws Exception {
        connection = new VeloxConnection(connectionFileTest);
        openConnection();
    }

    @After
    public void tearDown() throws Exception {
        if(sampleSetRecord != null) {
            dataRecordManager.deleteDataRecords(Arrays.asList(sampleSetRecord), null, true, user);
            dataRecordManager.storeAndCommit(String.format("Sample Set deleted: %s", validSampleSetId), user);
        }
        closeConnection();
    }

    @Test
    public void whenSampleSetDoesntExist_shouldThrowAnException() throws Exception {
        //when
        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        //then
        assertThat(exception.isPresent(), is(true));
    }

    @Test
    public void whenSampleSetHasNoSamplesNorRequests_shouldReturnRequestWithNoSamples() throws Exception {
        addSampleSetRecord();
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getId(), is(validSampleSetId));
        assertThat(request.getSamples().size(), is(0));
    }

    @Test
    public void whenSampleSetHasOneRequestAndNoPrimaryRequestSet_shouldThrowAnException() throws Exception {
        addSampleSetRecord();
        addChildToSampleSet(recordId_02756_b);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
    }

    @Test
    public void whenSampleSetHasOneRequestWhichIsPrimaryRequest_shouldReturnRequestWithSamplesFromThisRequest() throws Exception {
        addSampleSetRecord();
        DataRecord reqRecord = addChildToSampleSet(recordId_02756_b);
        addPrimaryRequest(reqId_02756_b);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getSamples().size(), is(getSamplesFromRequest(reqRecord).size()));
    }

    @Test
    public void whenSampleSetHasOneSampleFromRequestWhichIsPrimaryRequest_shouldReturnRequestWithOneSample() throws Exception {
        addSampleSetRecord();
        addChildToSampleSet(sampleRecordId_02756_b_1);
        addPrimaryRequest(reqId_02756_b);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getSamples().size(), is(1));
    }

    @Test
    public void whenSampleSetHasOneSampleFromRequestWhichIsNotPrimaryRequest_shouldThrowException() throws Exception {
        addSampleSetRecord();
        addChildToSampleSet(sampleRecordId_02756_b_1);
        addPrimaryRequest(reqId_04252_J);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSetProjectInfoConverter.PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenSampleSetPrimaryRequestIsNotPartOfSet_shouldThrowAnException() throws Exception {
        addSampleSetRecord();
        addChildToSampleSet(recordId_02756_b);
        addPrimaryRequest(reqId_04252_J);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSetProjectInfoConverter.PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenSampleSetHasTwoRequests_shouldReturnRequestWithSamplesFromBothOfThem() throws Exception {
        addSampleSetRecord();
        addRequestToSampleSet(request_05667_AB);
        addRequestToSampleSet(request_05667_AT);
        addPrimaryRequest(request_05667_AB);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        List<DataRecord> samplesFromReq1 = getSamplesFromRequest(request_05667_AB);
        List<DataRecord> samplesFromReq2 = getSamplesFromRequest(request_05667_AT);
        assertThat(request.getSamples().size(), is(samplesFromReq1.size() + samplesFromReq2.size()));
    }

    private List<DataRecord> getSamplesFromRequest(String requestId) throws Exception {
        List<DataRecord> dataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" + requestId + "'", user);
        return getSamplesFromRequest(dataRecords.get(0));
    }

    private void addRequestToSampleSet(String requestId) throws Exception {
        List<DataRecord> dataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" + requestId + "'", user);

        sampleSetRecord.addChild(dataRecords.get(0), user);
    }

    private void addPrimaryRequest(String reqId) throws Exception {
        sampleSetRecord.setDataField(VeloxConstants.PRIME_REQUEST, reqId, user);
    }

    private List<DataRecord> getSamplesFromRequest(DataRecord reqRecord) throws Exception {
        return Arrays.asList(reqRecord.getChildrenOfType(VeloxConstants.SAMPLE, user));
    }

    private DataRecord addChildToSampleSet(int reqRecordId) throws Exception {
        DataRecord reqRecord = dataRecordManager.querySystemForRecordId(reqRecordId, user);

        sampleSetRecord.addChild(reqRecord, user);

        return reqRecord;
    }

    private void addSampleSetRecord() throws Exception {
        sampleSetRecord = dataRecordManager.addDataRecord(VeloxConstants.SAMPLE_SET, user);
        sampleSetRecord.setDataField("Name", validSampleSetId, user);
        sampleSetRecord.setDataField(VeloxConstants.RECIPE, Recipe.AMPLI_SEQ.getValue(), user);
    }

    private void store() throws ServerException, RemoteException, VeloxConnectionException {
        dataRecordManager.storeAndCommit(String.format("Sample Set added: %s", validSampleSetId), user);
    }

    private void openConnection() throws VeloxConnectionException {
        if (!connection.isConnected()) {
            connection.open();
        }

        dataRecordManager = connection.getDataRecordManager();
        user = connection.getUser();
    }

    private void closeConnection() throws VeloxConnectionException {
        connection.close();
        LOG.info("Closed LIMS connection");
    }
}
