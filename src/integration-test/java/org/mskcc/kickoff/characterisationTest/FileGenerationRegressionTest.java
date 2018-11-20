package org.mskcc.kickoff.characterisationTest;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mskcc.domain.Patient;
import org.mskcc.kickoff.characterisationTest.comparator.LinesEqualExceptPathsAndDatesPredicate;
import org.mskcc.kickoff.generator.FileManifestGenerator;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.ContextConfiguration;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * JIRA upload logic is not tested here and thus relevant beans are not imported
 */
@RunWith(Parameterized.class)
@ContextConfiguration(classes = RegressionTestConfig.class)
public class FileGenerationRegressionTest {

    private static ApplicationContext context;
    private static String manifestOutputFilePath;
    private static String manifestGoldenFilePath;
    private static String projectIds;
    private String projectId;
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public FileGenerationRegressionTest(String projectId) {
        this.projectId = projectId;
    }

    @Parameterized.Parameters(name = "Testing projectId: {0}")
    public static Iterable<String> params() {
        setupApplicationContext();
        return Arrays.asList(projectIds.split("\\s*,\\s*"));
    }

    private static void setupApplicationContext() {
        context = new AnnotationConfigApplicationContext(RegressionTestConfig.class);
        ((AnnotationConfigApplicationContext) context).registerShutdownHook();
        manifestOutputFilePath = context.getEnvironment().getProperty("test.integration.manifestOutputFilePath");
        manifestGoldenFilePath = context.getEnvironment().getProperty("test.integration.manifestGoldenFilePath");
        projectIds = context.getEnvironment().getProperty("test.integration.projectIds");
    }

    @Before
    public void setupManifestFiles() throws Exception {
        deleteOldDirectory();
        DEV_LOGGER.info(String.format("Generating manifest files for project <%s>.", projectId));
        generateManifestFiles(projectId);
    }

    @After
    public void tearDown() {
        resetPatientGroupNumber();
        deleteOldDirectory();
    }

    @Test
    public void whenRunningCreateManifestSheet_outputFilesShouldBeAsBefore() throws Exception {
        String expectedOutputPath = String.join(File.separator,
                manifestGoldenFilePath, Constants.PROJECT_PREFIX + projectId);
        String actualOutputPath = String.join(File.separator,
                manifestOutputFilePath, Constants.PROJECT_PREFIX + projectId);
        DEV_LOGGER.info(String.format("Comparing file content for project <%s>, expected path <%s>, actual path <%s>.", projectId, expectedOutputPath, actualOutputPath));

        List<File> expectedFiles = getManifestFiles(expectedOutputPath);
        List<File> actualFiles = getManifestFiles(actualOutputPath);
 
        assertOutputFilesExist(actualFiles, expectedFiles);
        assertOutputFilesContent(actualFiles, expectedFiles);
    }

    private void generateManifestFiles(String projectId) throws Exception {
        FileManifestGenerator fileManifestGenerator = context.getBean(FileManifestGenerator.class);
        fileManifestGenerator.generate(projectId);
    }

    private List<File> getManifestFiles(String path) {
        return Utils.getFilesInDir(new File(path),
                p -> p.getFileName().toString().startsWith(Constants.PROJECT_PREFIX)
                     && p.getFileName().toString().endsWith(".txt")
                    && !p.getFileName().toString().contains("c_to_p"));
    }

    private void assertOutputFilesExist(List<File> actualFiles, List<File> expectedFiles) {
        assertThat(actualFiles.size(), is(expectedFiles.size()));
    }

    private void assertOutputFilesContent(List<File> actualFiles, List<File> expectedFiles) {
        actualFiles.sort(Comparator.comparing(File::getName));
        expectedFiles.sort(Comparator.comparing(File::getName));

        LinesEqualExceptPathsAndDatesPredicate predicate = new LinesEqualExceptPathsAndDatesPredicate(
                Paths.get(manifestOutputFilePath), Paths.get(manifestGoldenFilePath));
        for (int i = 0; i < actualFiles.size(); i ++) {
            assertThat(String.format("File content not same for actual <%s> and expected <%s>.",
                    actualFiles.get(i), expectedFiles.get(i)),
                    contentEquals(actualFiles.get(i), expectedFiles.get(i), predicate));
        }
    }

    private boolean contentEquals(File file1, File file2, LinesEqualExceptPathsAndDatesPredicate predicate) {
        try (BufferedReader readerFile1 = new BufferedReader(new FileReader(file1));
             BufferedReader readerFile2 = new BufferedReader(new FileReader(file2))) {
            String lineFile1 = null;
            while ((lineFile1 = readerFile1.readLine()) != null) {
                String lineFile2 = readerFile2.readLine();
                if (!predicate.test(lineFile1, lineFile2)) {
                    DEV_LOGGER.info(String.format("File actual <%s> and expect <%s>:", file1.getName(), file2.getName()));
                    DEV_LOGGER.info(String.format("Line mismatch actual <%s> and expect <%s>.", lineFile1, lineFile2));
                    return false;
                }
            }
        } catch (IOException e) {
            DEV_LOGGER.info("File not found: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void resetPatientGroupNumber() {
        try{
            Field field = Patient.class.getDeclaredField("count");
            field.setAccessible(true);
            field.set(null, 0);
        } catch (Exception e) {}
    }

    private void deleteOldDirectory() {
        String actualOutputPath = String.join(File.separator,
                manifestOutputFilePath, Constants.PROJECT_PREFIX + projectId);
        try {
            FileUtils.deleteDirectory(new File(actualOutputPath));
        } catch (IOException e) {
            DEV_LOGGER.info(e.getMessage());
        }
    }
}