package org.mskcc.kickoff.fast.sampleset;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxConnectionException;
import com.velox.sapioutils.client.standalone.VeloxStandaloneManagerContext;
import com.velox.sapioutils.shared.managers.DataRecordUtilManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.object.IsCompatibleType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.QcStatus;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.RequestSpecies;
import org.mskcc.domain.SampleSet;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.retriever.RecordNimblegenResolver;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.sampleset.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.Sample2DataRecordMap;
import org.mskcc.kickoff.velox.VeloxConnectionData;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.mskcc.util.TestUtils;
import org.mskcc.util.VeloxConstants;

import java.io.FileInputStream;
import java.io.InputStream;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiPredicate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class SampleSetIntegrationTest {
    private static final Log LOG = LogFactory.getLog(SampleSetIntegrationTest.class);
    private static final String resultsPathPrefix = "/ifs/solres/seq";
    private static String connectionFile = "/lims-tango-dev.properties";
    private static String connectionFileTest = "/lims-tango-test.properties";
    private static int counter = 0;
    private final String reqId1 = "12345_S";
    private final String reqId2 = "23456_S";
    private String validSampleSetId = "set_SampleSetTest";
    private VeloxProjectProxy veloxProjectProxy;
    private DataRecord sampleSetRecord;
    private VeloxConnection connection;
    private DataRecordManager dataRecordManager;
    private DataRecordUtilManager dataRecUtilManager;
    private User user;

    @Before
    public void setUp() throws Exception {
        veloxProjectProxy = getVeloxProjectProxy();
        VeloxConnectionData veloxConnectionData = getVeloxConnectionData(connectionFileTest);
        connection = new VeloxConnection(
                veloxConnectionData.getHost(),
                veloxConnectionData.getPort(),
                veloxConnectionData.getGuid(),
                veloxConnectionData.getUsername(),
                veloxConnectionData.getPassword()
        );
        openConnection();
        try {
            cleanupTestingRecords();
        } catch (Exception e) {
            LOG.error(e);
        }
    }

    private VeloxProjectProxy getVeloxProjectProxy() throws Exception {
        Properties prop = new Properties();
        prop.load(new FileInputStream("src/main/resources/application-dev.properties"));
        String designFilePath = prop.getProperty("designFilePath");

        ProjectFilesArchiver archiverMock = mock(ProjectFilesArchiver.class);
        ProjectInfoRetriever projInfoRetriever = new ProjectInfoRetriever();
        RequestDataPropagator reqDataPropagator = new RequestDataPropagator(designFilePath, resultsPathPrefix,
                mock(ErrorRepository.class), (bs1, bs2) -> true);
        SampleSetProjectInfoConverter projInfoConv = new SampleSetProjectInfoConverter();
        SampleSetToRequestConverter sampleSetToReqConv = new SampleSetToRequestConverter(
                projInfoConv,
                (s1, s2) -> true,
                mock(ErrorRepository.class));
        RequestsRetrieverFactory requestsRetrieverFactory = new RequestsRetrieverFactory(projInfoRetriever,
                reqDataPropagator, reqDataPropagator, sampleSetToReqConv, mock(ReadOnlyExternalSamplesRepository.class),
                mock(BiPredicate.class), mock(BiPredicate.class), mock(ErrorRepository.class), new
                RecordNimblegenResolver(),
                new Sample2DataRecordMap());
        return new VeloxProjectProxy(getVeloxConnectionData(connectionFile), archiverMock,
                requestsRetrieverFactory, "");
    }

    @After
    public void tearDown() throws Exception {
        try {
            cleanupTestingRecords();
        } finally {
            closeConnection();
        }
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
        addSampleSetRecord(validSampleSetId);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getId(), is(validSampleSetId));
        assertThat(request.getSamples().size(), is(0));
    }

    @Test
    public void whenSampleSetHasOneRequestAndNoPrimaryRequestSet_shouldThrowAnException() throws Exception {
        addSampleSetRecord(validSampleSetId);
        addRequest(this.reqId1);

        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
    }

    @Test
    public void whenSampleSetHasOneRequestWhichIsPrimaryRequest_shouldReturnRequestWithSamplesFromThisRequest()
            throws Exception {
        addSampleSetRecord(validSampleSetId);
        addRequest(reqId1);
        addPrimaryRequest(reqId1);

        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getSamples().size(), is(getSamplesFromRequest(reqId1).size()));
    }

    @Test
    public void whenSampleSetHasOneSampleFromRequestWhichIsPrimaryRequest_shouldReturnRequestWithOneSample() throws
            Exception {
        addSampleSetRecord(validSampleSetId);
        addPassedSample(reqId1);
        addPrimaryRequest(reqId1);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        assertThat(request.getSamples().size(), is(1));
    }

    @Test
    public void whenSampleSetHasOneSampleFromRequestWhichIsNotPrimaryRequest_shouldThrowException() throws Exception {
        addSampleSetRecord(validSampleSetId);
        addPassedSample(reqId1);

        addPrimaryRequest(reqId2);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));

        assertThat(ExceptionUtils.getRootCause(exception.get()).getClass(), IsCompatibleType.typeCompatibleWith(SampleSet
                .PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenSamplesInPairingHaveIncompatibleBaitSets_shouldSetError() throws Exception {
        addSampleSetRecord(validSampleSetId);

    }

    @Test
    public void whenSampleSetPrimaryRequestIsNotPartOfSet_shouldThrowAnException() throws Exception {
        addSampleSetRecord(validSampleSetId);
        addRequest(reqId1);
        addPrimaryRequest(reqId2);
        store();

        Optional<Exception> exception = TestUtils.assertThrown(() -> veloxProjectProxy.getRequest(validSampleSetId));

        assertThat(exception.isPresent(), is(true));
        assertThat(ExceptionUtils.getRootCause(exception.get()).getClass(), IsCompatibleType.typeCompatibleWith(SampleSet
                .PrimaryRequestNotPartOfSampleSetException.class));
    }

    @Test
    public void whenSampleSetHasTwoRequests_shouldReturnRequestWithSamplesFromBothOfThem() throws Exception {
        addSampleSetRecord(validSampleSetId);

        addRequest(reqId1);
        addRequest(reqId2);

        addPrimaryRequest(reqId1);
        store();

        KickoffRequest request = veloxProjectProxy.getRequest(validSampleSetId);

        List<DataRecord> samplesFromReq1 = getSamplesFromRequest(reqId1);
        List<DataRecord> samplesFromReq2 = getSamplesFromRequest(reqId2);
        assertThat(request.getSamples().size(), is(samplesFromReq1.size() + samplesFromReq2.size()));
    }

    private void addSampleSetRecord(String sampleSetId) throws Exception {
        sampleSetRecord = dataRecordManager.addDataRecord(VeloxConstants.SAMPLE_SET, user);
        sampleSetRecord.setDataField("Name", sampleSetId, user);
        sampleSetRecord.setDataField(VeloxConstants.RECIPE, Recipe.IMPACT_410.getValue(), user);
    }

    private void addRequest(String reqId) throws Exception {
        DataRecord request = dataRecordManager.addDataRecord(VeloxConstants.REQUEST, user);
        request.setDataField(VeloxConstants.REQUEST_ID, reqId, user);
        request.setDataField(VeloxConstants.REQUEST_NAME, "IMPACT410", user);

        addPassedSample(request, reqId);
        addPassedSample(request, reqId);

        sampleSetRecord.addChild(request, user);
    }

    private DataRecord addPassedSample(DataRecord request, String reqId) throws IoError, NotFound, AlreadyExists,
            InvalidValue, ServerException, RemoteException {
        DataRecord sampleRecord = request.addChild(VeloxConstants.SAMPLE, user);
        String otherSampleId = String.format("%s_%d", reqId, counter++);

        sampleRecord.setDataField(VeloxConstants.OTHER_SAMPLE_ID, otherSampleId, user);
        sampleRecord.setDataField(VeloxConstants.SAMPLE_ID, "igoId_" + counter++, user);
        sampleRecord.setDataField(VeloxConstants.SPECIES, RequestSpecies.HUMAN.getValue(), user);
        sampleRecord.setDataField(Sample.TUMOR_TYPE, "OMGCT,OOVC,VMGCT,VIMT", user);
        sampleRecord.setDataField(VeloxConstants.RECIPE, "IMPACT410", user);
        sampleRecord.setDataField(Sample.EXEMPLAR_SAMPLE_TYPE, "DNA", user);

        addPassedSampleQc(reqId, sampleRecord, otherSampleId);

        return sampleRecord;
    }

    private void addPassedSampleQc(String reqId, DataRecord sampleRecord, String otherSampleId) throws IoError,
            NotFound, AlreadyExists, InvalidValue, ServerException, RemoteException {
        DataRecord sampleQc = sampleRecord.addChild(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, user);

        sampleQc.setDataField(VeloxConstants.SEQ_QC_STATUS, QcStatus.PASSED.getValue(), user);
        sampleQc.setDataField(VeloxConstants.REQUEST, reqId, user);
        sampleQc.setDataField(VeloxConstants.OTHER_SAMPLE_ID, otherSampleId, user);
        sampleQc.setDataField(VeloxConstants.SEQUENCER_RUN_FOLDER, "koty_sa_najlepsze", user);
    }

    private void addPassedSample(String requestId) throws Exception {
        DataRecord request = dataRecordManager.addDataRecord(VeloxConstants.REQUEST, user);
        request.setDataField(VeloxConstants.REQUEST_ID, requestId, user);
        request.setDataField(VeloxConstants.REQUEST_NAME, "IMPACT410", user);

        DataRecord sampleRecord = addPassedSample(request, requestId);

        sampleSetRecord.addChild(sampleRecord, user);
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

    private void store() throws ServerException, RemoteException, VeloxConnectionException {
        dataRecordManager.storeAndCommit(String.format("Sample Set added: %s", validSampleSetId), user);
    }

    private void openConnection() throws Exception {
        // add shutdown hook: for connection close
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Run shutdown hook");
            try {
                closeConnection();
            } catch (Exception e) {
                LOG.error(e);
            }
        }));

        if (!connection.isConnected()) {
            connection.open();
        }

        dataRecordManager = connection.getDataRecordManager();
        DataMgmtServer dataMgmtServer = connection.getDataMgmtServer();
        user = connection.getUser();
        dataRecUtilManager = new DataRecordUtilManager(new VeloxStandaloneManagerContext(user, dataMgmtServer));
    }

    private void closeConnection() throws VeloxConnectionException {
        if (connection.isConnected()) {
            connection.close();
        }
        LOG.info("Closed LIMS connection");
    }

    // delete inserted testing records
    private void cleanupTestingRecords() throws Exception{
        List<DataRecord> list = dataRecordManager.queryDataRecords(VeloxConstants.SAMPLE_SET, "NAME " +
                "= '" +
                validSampleSetId + "'", user);
        dataRecUtilManager.deleteRecords(list, true);

        List<DataRecord> reqRecords1 = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId " +
                "= '" +
                reqId1 + "'", user);
        dataRecUtilManager.deleteRecords(reqRecords1, true);

        List<DataRecord> reqRecords2 = dataRecordManager.queryDataRecords(VeloxConstants.REQUEST, "RequestId " +
                "= '" +
                reqId2 + "'", user);
        dataRecUtilManager.deleteRecords(reqRecords2, true);

        dataRecordManager.storeAndCommit(String.format("Sample Set deleted: %s", validSampleSetId), user);
    }
}
