package org.mskcc.kickoff.fast.endtoend;

import com.google.common.collect.Lists;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxConnectionException;
import org.apache.log4j.Logger;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.fast.sampleset.SampleSetIntegrationTest;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.lims.SampleInfo;
import org.mskcc.kickoff.printer.FastqPathsRetriever;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.resolver.PairednessResolver;
import org.mskcc.kickoff.retriever.FileSystemFastqPathsRetriever;
import org.mskcc.kickoff.retriever.ReadOnlyExternalSamplesRepository;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.sampleset.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.sampleset.SampleSetToRequestConverter;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.PairednessValidPredicate;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.VeloxConnectionData;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.BiPredicate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

@RunWith(Parameterized.class)
public class MappingFilePrinterTest {

    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final String connectionFile = "/lims-tango-dev.properties";
    private static final String connectionFileTest = "/lims-tango-test.properties";
    private static String fastq_path;
    private static String designFilePath;
    private static String resultsPathPrefix;

    private final String projectId;
    private KickoffRequest request;

    private ProjectFilesArchiver archiverMock = mock(ProjectFilesArchiver.class);
    private ProjectInfoRetriever projInfoRetriever = new ProjectInfoRetriever();
    private RequestDataPropagator reqDataPropagator = new RequestDataPropagator(designFilePath, resultsPathPrefix,
            mock(ErrorRepository.class), (bs1, bs2) -> true);
    private SampleSetProjectInfoConverter projInfoConv = new SampleSetProjectInfoConverter();
    private SampleSetToRequestConverter sampleSetToReqConv = new SampleSetToRequestConverter(
            projInfoConv,
            (s1, s2) -> true,
            mock(ErrorRepository.class));
    private RequestsRetrieverFactory requestsRetrieverFactory = new RequestsRetrieverFactory(projInfoRetriever,
            reqDataPropagator, reqDataPropagator, sampleSetToReqConv, mock(ReadOnlyExternalSamplesRepository.class),
            mock(BiPredicate.class), mock(BiPredicate.class), mock(ErrorRepository.class));
    private VeloxProjectProxy veloxProjectProxy;
    private MappingFilePrinter mappingFilePrinter;
    private VeloxConnection connection;
    private FastqPathsRetriever fastqPathsRetriever;

    public MappingFilePrinterTest(String projectId) {
        this.projectId = projectId;
    }

    @Before public void setUp() throws Exception {
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

        fastqPathsRetriever = new FileSystemFastqPathsRetriever(String.format("%s/hiseq/FASTQ/", fastq_path));
        mappingFilePrinter = new MappingFilePrinter(new PairednessValidPredicate(), new PairednessResolver(), mock(ObserverManager.class), fastqPathsRetriever);
    }

    @After public void tearDown() throws Exception {
        closeConnection();
    }

    @Parameterized.Parameters(name = "Testing mapping file content for projectId: {0}")
    public static Iterable<String> params() throws IOException {
        loadProperty();
        return Lists.newArrayList("06302_D", "04430_AI", "set_07737_C", "set_09049_D_1");
    }

    private static void loadProperty() throws IOException {
        Properties prop = new Properties();
        prop.load(new FileInputStream("src/main/resources/application-dev.properties"));
        fastq_path=prop.getProperty("fastq_path");
        designFilePath = prop.getProperty("designFilePath");
        resultsPathPrefix =prop.getProperty("resultsPathPrefix");
    }

    @Test public void whenSampleHasSequencingRuns_shouldReturnItsUniqueRunFolderForEachSample() throws Exception{
        request = veloxProjectProxy.getRequest(projectId);

        String mappingFileContents = ReflectionTestUtils.invokeMethod(mappingFilePrinter, "getMappings", request);
        String[] arr = mappingFileContents.split("\\n");
        Map<String, Set<String>> sampleRunsActual = new HashMap<>();
        Arrays.stream(arr).forEach(line -> {
            String[] temp = line.split("\\t");
            sampleRunsActual.computeIfAbsent(temp[1], s -> new HashSet<>()).add(temp[2]);
        });

        assertSequencingRunsMatch(sampleRunsActual);
    }

    private void assertSequencingRunsMatch(Map<String, Set<String>> sampleRunsActual) {
        Map<String, Set<String>> sampleRunsExp = getSampleRuns(request);
        assertThat(sampleRunsExp.size(), is(sampleRunsActual.size()));

        for (Map.Entry<String, Set<String>> entryExp: sampleRunsExp.entrySet()) {
            String sampleName = entryExp.getKey();
            assertThat(sampleRunsActual, Matchers.hasKey(sampleName));
            assertThat(sampleRunsActual.get(sampleName).size(), is(entryExp.getValue().size()));
            Set<String> setAct = sampleRunsActual.get(sampleName);
            entryExp.getValue().forEach(runid ->
                    assertThat(setAct.stream().anyMatch(s -> s.startsWith(runid)), is(true)));
        }
    }

    private Map<String, Set<String>> getSampleRuns(KickoffRequest singleRequest) {
        Map<String, String> sampleRenamesAndSwaps = SampleInfo.getSampleRenames();
        Map<String, Set<String>> sampleRuns = new HashMap<>();
        for (Sample sample : singleRequest.getAllValidSamples().values()) {
            for (String runId : sample.getValidRunIds()) {
                String sampleId = sample.getCmoSampleId();
                String sampleName = sampleNormalization(sampleRenamesAndSwaps.getOrDefault(sampleId,
                        sampleId));
                sampleRuns.computeIfAbsent(sampleName, s -> new HashSet<>()).add(runId);
            }
        }
        return sampleRuns;
    }

    private VeloxConnectionData getVeloxConnectionData(String connectionFile) throws Exception {
        Properties properties = new Properties();
        InputStream input = new FileInputStream(SampleSetIntegrationTest.class.getResource(connectionFile).getPath());
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

    private void openConnection() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            DEV_LOGGER.info("Run shutdown hook");
            try {
                closeConnection();
            } catch (Exception e) {
                DEV_LOGGER.error(e);
            }
        }));

        if (!connection.isConnected()) {
            connection.open();
        }
    }

    private void closeConnection() throws VeloxConnectionException {
        if (connection.isConnected()) {
            connection.close();
        }
        DEV_LOGGER.info("Closed LIMS connection");
    }
}
