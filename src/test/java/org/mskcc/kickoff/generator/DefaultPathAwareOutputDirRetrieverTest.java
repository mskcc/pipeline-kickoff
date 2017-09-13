package org.mskcc.kickoff.generator;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Test;
import org.mskcc.kickoff.util.Utils;

import java.io.File;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class DefaultPathAwareOutputDirRetrieverTest {
    private final String defaultPath = "src/test/resources/temp";

    @After
    public void tearDown() throws Exception {
        FileUtils.deleteDirectory(new File(defaultPath));
    }

    @Test
    public void whenOutputDirIsNotValid_shouldDefaultPathBeUsed() {
        DefaultPathAwareOutputDirRetriever defaultPathAwareOutputDirRetriever = new DefaultPathAwareOutputDirRetriever(defaultPath, s -> false);

        String projectId = "projecId";
        String outputDir = "";

        String outputPath = defaultPathAwareOutputDirRetriever.retrieve(projectId, outputDir);

        assertThat(outputPath, is(String.format("%s/%s", defaultPath, Utils.getFullProjectNameWithPrefix(projectId))));
        assertTrue(new File(outputPath).exists());
    }

    @Test
    public void whenOutputDirIsProvided_shouldThisPathBeUsed() {
        DefaultPathAwareOutputDirRetriever defaultPathAwareOutputDirRetriever = new DefaultPathAwareOutputDirRetriever(defaultPath, s -> true);

        String projectId = "projecId";
        String outputDir = "some/dir";

        String outputPath = defaultPathAwareOutputDirRetriever.retrieve(projectId, outputDir);

        assertThat(outputPath, is(String.format("%s/%s", outputDir, Utils.getFullProjectNameWithPrefix(projectId))));
    }

}