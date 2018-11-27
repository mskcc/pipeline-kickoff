package org.mskcc.kickoff.retriever;

import org.assertj.core.api.Assertions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

public class FileSystemFastqPathsRetrieverTest {
    private final String reqId1 = "12345";
    private final String reqId2 = "23456";
    private final String runId1 = "JAX_123";
    private final String runId2 = "PITT_123";
    private final String samplePattern = "12345_1";
    private final String fastqName = "fastq1";

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
    public void whenFastqsDirHasOneFastq_shouldReturnOneFastq() throws Exception {
        //given
        File sampleFastqs = createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                samplePattern);

        addFastqToDir(sampleFastqs);

        //when
        List<String> fastqs = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assert.assertThat(fastqs.size(), is(1));
        Assert.assertThat(fastqs.get(0), is(sampleFastqs.getPath()));
    }

    @Test
    public void whenFastqsDirHasOneFastqAndOthersForDifferentRunIds_shouldReturnOneFastq() throws Exception {
        //given
        File sampleFastqs = createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                samplePattern);

        createSubfolders(runId2, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);

        //when
        List<String> fastqs = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assert.assertThat(fastqs.size(), is(1));
        Assert.assertThat(fastqs.get(0), is(sampleFastqs.getPath()));
    }

    @Test
    public void whenFastqsDirHasNotExcactFastqDirs_shouldReturnOnlyMatching() throws Exception {
        //given
        List<String> correctFastqDirs = Arrays.asList(
                createSubfolders(runId1 + "ABS", Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                        samplePattern).getPath(),
                createSubfolders(runId1 + "whateva", Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" +
                        samplePattern).getPath(),
                createSubfolders(runId1, Constants.PROJECT_PREFIX + "sth" + reqId1, "Sample_" + samplePattern)
                        .getPath(),
                createSubfolders(runId1, Constants.PROJECT_PREFIX + "_-" + reqId1, "Sample_" + samplePattern).getPath(),
                createSubfolders(runId1, Utils.getFullProjectNameWithPrefix("0" + reqId1), "Sample_" + samplePattern)
                        .getPath()
        );

        createSubfolders("whateva" + runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, "My" + Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1) + "something", "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), "Sample" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId1), samplePattern);
        createSubfolders(runId1, reqId1, "Sample_" + samplePattern);
        createSubfolders(runId2, Utils.getFullProjectNameWithPrefix(reqId1), "Sample_" + samplePattern);
        createSubfolders(runId1, Utils.getFullProjectNameWithPrefix(reqId2), "Sample_" + samplePattern);

        //when
        List<String> fastqs = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assertions.assertThat(fastqs).containsOnlyElementsOf(correctFastqDirs);
    }

    @Test
    public void whenFastqDirDoesntContainDirsWithOnlySampleIdsButWithIgoAndSeqId_shouldReturnIgoAndSeqIdOnes() throws
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
                createSubfolders(runId1, Utils.getFullProjectNameWithPrefix("0" + reqId1), "Sample_" + samplePattern
                        + "_IGO_" + seqIgoId).getPath()
        );

        Assert.assertThat(sample.get(Constants.MANIFEST_SAMPLE_ID), is("IGO_" + igoId));

        //when
        List<String> fastqs = retriever.retrieve(request, sample, runId1, samplePattern);

        //then
        Assertions.assertThat(fastqs).containsOnlyElementsOf(correctFastqDirs);
        Assert.assertThat(sample.get(Constants.MANIFEST_SAMPLE_ID), is("IGO_" + seqIgoId));
    }

    private File createSubfolders(String runId, String projectId, String sampleId) throws IOException {
        return temporaryFolder.newFolder(runId, projectId, sampleId);
    }

    private void addFastqToDir(File sampleFastqs) throws IOException {
        TemporaryFolder temporaryFolder = new TemporaryFolder(sampleFastqs);
        temporaryFolder.create();
        temporaryFolder.newFile(fastqName);
    }

}