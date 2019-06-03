package org.mskcc.kickoff.retriever;

import org.assertj.core.api.Assertions;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import org.apache.log4j.Logger;
import java.util.Date;
import static org.mockito.Mockito.mock;
import java.nio.file.attribute.FileTime;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

public class FileSystemFastqPathsRetrieverTest {
    private static final Logger LOGGER = Logger.getLogger(FileSystemFastqPathsRetrieverTest.class);

    private final String reqId1 = "12345";
    private final String reqId2 = "23456";
    private final String runId1 = "JAX_123";
    private final String runId2 = "PITT_123";
    private final String samplePattern = "12345_1";
    private final String fastqName = "fastq1";
    private int counter = 1;

    @Rule
    public TemporaryFolder temporaryFolder;

    private FileSystemFastqPathsRetriever retriever;
    private KickoffRequest request;
    private Sample sample;
    private String seqIgoId = "A_1";
    private String igoId = "12345_A_1";

    @Before
    public void setUp() throws Exception {
        temporaryFolder = new TemporaryFolder();
        temporaryFolder.create();
        retriever = new FileSystemFastqPathsRetriever(temporaryFolder.getRoot().getPath());
        request = new KickoffRequest(reqId1, mock(ProcessingType.class));
        sample = new Sample("12345_1");
        sample.setRequestId(reqId1);
        sample.put(Constants.SEQ_IGO_ID, seqIgoId);
        sample.put(Constants.IGO_ID, igoId);
        sample.put(Constants.MANIFEST_SAMPLE_ID, "IGO_" + igoId);
    }

    @After
    public void tearDown() {
        if (temporaryFolder != null) {
            temporaryFolder.delete();
        }
    }

    @Test
    public void whenNoFastqsAreAvailable_shouldThrowException() throws Exception {
        Assertions.assertThatThrownBy(() -> retriever.retrieve(request, sample, runId1, samplePattern))
                .isInstanceOf(FileSystemFastqPathsRetriever.FastqDirNotFound.class);
    }

    @Test
    public void whenFastqDirContainsOnlyNotMatchingFastqs_shouldThrowException() throws Exception {
        createSubfolders(runId2, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders("bazinga" + runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, reqId1, "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId2), "Sample_" + samplePattern);

        Assertions.assertThatThrownBy(() -> retriever.retrieve(request, sample, runId1, samplePattern))
                .isInstanceOf(FileSystemFastqPathsRetriever.FastqDirNotFound.class);
    }

    @Test
    public void whenFastqsDirHasOneFastq_shouldReturnThisFastq() throws Exception {
        //given
        File sampleFastqs = createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                samplePattern);

        addFastqToDir(sampleFastqs);

        //when
        String fastq = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assertions.assertThat(fastq).isEqualTo(sampleFastqs.getPath());
    }

    @Test
    public void whenFastqsDirHasOneFastqAndOthersForDifferentRunIds_shouldReturnThisFastq() throws Exception {
        //given
        File sampleFastqs = createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                samplePattern);

        createSubfolders(runId2, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);

        //when
        String fastq = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assertions.assertThat(fastq).isEqualTo(sampleFastqs.getPath());
    }

    @Test
    public void whenFastqsDirHasNotExcactFastqDirs_shouldReturnLatestMatching() throws Exception {
        //given

        List<String> correctFastqDirs = new LinkedList<>();
        correctFastqDirs.add(createSubfolders(runId1 + "ABS", Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                samplePattern).getPath());
        correctFastqDirs.add(createSubfolders(runId1 + "whateva", Utils.getFullProjectNameWithPrefix(reqId1),
                "Sample_" + samplePattern).getPath());
        correctFastqDirs.add(createSubfolders(runId1, Constants.PROJECT_PREFIX + "sth" + reqId1,
                "Sample_" + samplePattern).getPath());
        correctFastqDirs.add(createSubfolders(runId1, Constants.PROJECT_PREFIX + "_-" + reqId1,
                "Sample_" + samplePattern).getPath());
        correctFastqDirs.add(createSubfolders(runId1, Utils.getFullProjectNameWithPrefix("0" + reqId1),
                        "Sample_" + samplePattern).getPath());

        createSubfolders("whateva" + runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, "My" + Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1) + "something", "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), samplePattern);
        createSubfolders(runId1, reqId1, "Sample_" + samplePattern);
        createSubfolders(runId2, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId2), "Sample_" + samplePattern);

        //when
        String fastqs = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assertions.assertThat(fastqs).isEqualTo(correctFastqDirs.get(correctFastqDirs.size() - 1));
    }

    @Test
    public void whenFastqDirDoesntContainDirsWithOnlySampleIdsButWithIgoAndSeqId_shouldReturnIgoAndSeqIdLatesOne() throws
            Exception {
        //given
        
        List<String> correctFastqDirs = Arrays.asList(
                createSubfolders(runId1 + "ABS", Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                        samplePattern + "_IGO_" + seqIgoId).getPath(),
                createSubfolders(runId1 + "ABS", Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                        samplePattern + "_IGO_" + seqIgoId + "whateva").getPath(),
                createSubfolders(runId1 + "whateva", Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                        samplePattern + "_IGO_" + seqIgoId).getPath(),
                createSubfolders(runId1, Constants.PROJECT_PREFIX + "sth" + reqId1, "Sample_" + samplePattern +
                        "_IGO_" + seqIgoId).getPath(),
                createSubfolders(runId1, Constants.PROJECT_PREFIX + "_-" + reqId1, "Sample_" + samplePattern +
                        "_IGO_" + seqIgoId).getPath(),
                createSubfolders(runId1, Utils.getFullProjectNameWithPrefix("0" + reqId1),
                        "Sample_" + samplePattern + "_IGO_" + seqIgoId).getPath()
        );

        Assertions.assertThat(sample.get(Constants.MANIFEST_SAMPLE_ID)).isEqualTo("IGO_" + igoId);

        //when
        String fastqs = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assertions.assertThat(fastqs).isEqualTo(correctFastqDirs.get(correctFastqDirs.size() - 1));
        Assertions.assertThat(sample.get(Constants.MANIFEST_SAMPLE_ID)).isEqualTo("IGO_" + seqIgoId);
    }

    private File createSubfolders(String runId, String projectId, String sampleId) throws IOException {
        File folder = temporaryFolder.newFolder(runId, projectId, sampleId);
       
        //temporary solution because getLastModifiedTime cuts milliseconds
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {

        }
        
        long now = System.currentTimeMillis();
        FileTime nowMillis = FileTime.fromMillis(now);
        Files.setLastModifiedTime(Paths.get(folder.getPath()), nowMillis);
        FileTime lastModified = Files.getLastModifiedTime(Paths.get(folder.getPath()));

        LOGGER.info(String.format("Temp folder created: %s, last modified: %s", folder.getPath(), lastModified.toMillis()));

        return folder;
    }

    private void addFastqToDir(File sampleFastqs) throws IOException {
        TemporaryFolder temporaryFolder = new TemporaryFolder(sampleFastqs);
        temporaryFolder.create();
        temporaryFolder.newFile(fastqName);
    }
}