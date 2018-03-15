package org.mskcc.kickoff.fast.sampleset;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxConnectionException;
import org.apache.commons.lang3.StringUtils;
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
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.VeloxConnectionData;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.mskcc.util.TestUtils;
import org.mskcc.util.VeloxConstants;
import org.mskcc.domain.SampleSet;
import org.mskcc.domain.SampleSet.PrimaryRequestNotPartOfSampleSetException;

import java.io.FileInputStream;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class SampleSetIntegrationTest {
    private static final Log LOG = LogFactory.getLog(SampleSetIntegrationTest.class);
    private static String connectionFile = "/lims-tango-dev.properties";
    private static String connectionFileTest = "/lims-tango-test.properties";
    private final int recordId_03498_D = 278959;
    private final String reqId_03498_D = "03498_D";
    private final String reqId_04252_J = "04252_J";
    private final int sampleRecordId_03498_D_1 = 279030;
    private String validSampleSetId;
    private String designFilePath = "/ifs/projects/CMO/targets/designs";
    private String resultsPathPrefix = "/ifs/solres/seq";
    private ProjectFilesArchiver archiverMock = mock(ProjectFilesArchiver.class);
    private ProjectInfoRetriever projInfoRetriever = new ProjectInfoRetriever();
    private RequestDataPropagator reqDataPropagator = new RequestDataPropagator(designFilePath, resultsPathPrefix);
    private SampleSetProjectInfoConverter projInfoConv = new SampleSetProjectInfoConverter();
    private SampleSetToRequestConverter sampleSetToReqConv = new SampleSetToRequestConverter(projInfoConv);
    private RequestsRetrieverFactory requestsRetrieverFactory = new RequestsRetrieverFactory(projInfoRetriever,
            reqDataPropagator, sampleSetToReqConv, mock(ReadOnlyExternalSamplesRepository.class));
    private VeloxProjectProxy veloxProjectProxy;
    private DataRecord sampleSetRecord;
    private VeloxConnection connection;
    private DataRecordManager dataRecordManager;
    private User user;
    private String request_05667_AB = "05667_AB";
    private String request_05667_AT = "05667_AT";

    @Before
    public void setUp() throws Exception {
        veloxProjectProxy = new VeloxProjectProxy(getVeloxConnectionData(connectionFile), archiverMock,
                requestsRetrieverFactory);
        VeloxConnectionData veloxConnectionData = getVeloxConnectionData(connectionFileTest);
        connection = new VeloxConnection(
                veloxConnectionData.getHost(),
                veloxConnectionData.getPort(),
                veloxConnectionData.getGuid(),
                veloxConnectionData.getUsername(),
                veloxConnectionData.getPassword()
        );
        openConnection();
    }

    @After
    public void tearDown() throws Exception {
        if (sampleSetRecord != null) {
            dataRecordManager.deleteDataRecords(Arrays.asList(sampleSetRecord), null, true, user);
            dataRecordManager.storeAndCommit(String.format("Sample Set deleted: %s", validSampleSetId), user);
        }
        closeConnection();
    }

    private VeloxConnectionData getVeloxConnectionData(String connectionFile) throws Exception {
        Properties properties = new Properties();
        InputStream input = new FileInputStream(SampleSetIntegrationTest.class.getResource(connectionFile).getPath()
                .toString());
        properties.load(input);

        VeloxConnectionData veloxConnectionData = new VeloxConnectionData(
                (String) properties.get("lims.host"),
                Integer.parseInt((String) properties.get("lims.port")),
                (String) properties.get("lims.username"),
                (String) properties.get("lims.password"),
                (String) properties.get("lims.guid")
        );

        return veloxConnectionData;
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
        List<String> requests = Arrays.asList("NA");

        addSampleSetRecord(requests);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getId(), is(validSampleSetId));
        assertThat(request.getSamples().size(), is(0));
    }

    @Test
    public void whenSampleSetHasOneRequestAndNoPrimaryRequestSet_shouldThrowAnException() throws Exception {
        List<String> requests = Arrays.asList(reqId_03498_D);

        addSampleSetRecord(requests);
        addChildToSampleSet(recordId_03498_D);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
    }

    @Test
    public void whenSampleSetHasOneRequestWhichIsPrimaryRequest_shouldReturnRequestWithSamplesFromThisRequest()
            throws Exception {
        List<String> requests = Arrays.asList(reqId_03498_D);

        addSampleSetRecord(requests);
        DataRecord reqRecord = addChildToSampleSet(recordId_03498_D);
        addPrimaryRequest(reqId_03498_D);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getSamples().size(), is(getSamplesFromRequest(reqRecord).size()));
    }

    @Test
    public void whenSampleSetHasOneSampleFromRequestWhichIsPrimaryRequest_shouldReturnRequestWithOneSample() throws
            Exception {
        List<String> requests = Arrays.asList(reqId_03498_D);

        addSampleSetRecord(requests);
        addChildToSampleSet(sampleRecordId_03498_D_1);
        addPrimaryRequest(reqId_03498_D);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getSamples().size(), is(1));
    }

    @Test
    public void whenSampleSetHasOneSampleFromRequestWhichIsNotPrimaryRequest_shouldThrowException() throws Exception {
        List<String> requests = Arrays.asList(reqId_03498_D);

        addSampleSetRecord(requests);
        addChildToSampleSet(sampleRecordId_03498_D_1);
        addPrimaryRequest(reqId_04252_J);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSet
                .PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenSampleSetPrimaryRequestIsNotPartOfSet_shouldThrowAnException() throws Exception {
        List<String> requests = Arrays.asList(reqId_03498_D);

        addSampleSetRecord(requests);
        addChildToSampleSet(recordId_03498_D);
        addPrimaryRequest(reqId_04252_J);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(SampleSet
                .PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenSampleSetHasTwoRequests_shouldReturnRequestWithSamplesFromBothOfThem() throws Exception {
        List<String> requests = Arrays.asList(request_05667_AB, request_05667_AT);
        addSampleSetRecord(requests);

        for (String request : requests)
            addRequestToSampleSet(request);

        addPrimaryRequest(request_05667_AB);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        List<DataRecord> samplesFromReq1 = getSamplesFromRequest(request_05667_AB);
        List<DataRecord> samplesFromReq2 = getSamplesFromRequest(request_05667_AT);
        assertThat(request.getSamples().size(), is(samplesFromReq1.size() + samplesFromReq2.size()));
    }

    private List<DataRecord> getSamplesFromRequest(String requestId) throws Exception {
        List<DataRecord> dataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" +
                requestId + "'", user);
        return getSamplesFromRequest(dataRecords.get(0));
    }

    private void addRequestToSampleSet(String requestId) throws Exception {
        List<DataRecord> dataRecords = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" +
                requestId + "'", user);

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

    private void addSampleSetRecord(List<String> requests) throws Exception {
        sampleSetRecord = dataRecordManager.addDataRecord(VeloxConstants.SAMPLE_SET, user);
        validSampleSetId = "set_" + StringUtils.join(requests, "_");
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
