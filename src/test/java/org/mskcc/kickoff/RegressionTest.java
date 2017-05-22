package org.mskcc.kickoff;

import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.mskcc.kickoff.util.Utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.BiPredicate;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class RegressionTest {
    private static final Logger LOGGER = Logger.getLogger(RegressionTest.class);
    private static final SimpleDateFormat DATE_AND_TIME_FORMAT = new SimpleDateFormat("yyyy-dd-MM HH:mm:ss");
    private static final SimpleDateFormat DATE_ARCHIVE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    private static final String PROJECT_PROPERTY = "project";
    private static final String EXPECTED_OUTPUT_PROPERTY = "expectedOutput";
    private static final String ACTUAL_OUTPUT_PROPERTY = "actualOutput";
    private static final String SUCCEEDED_PROJECTS_LIST_PATH = "succeededProjectsList";
    private static final String FAILING_OUTPUT_PATH = "failingOutputPath";
    private static final String ARG = "arg";
    private final Date testStartTime = new Date();

    @Rule
    public WriteToTestReportOnFailure ruleExample = new WriteToTestReportOnFailure();

    private boolean isShiny = false;
    private String project = "06907_J";
    private Path expectedOutputPath = Paths.get("src/test/resources/expectedOutput/06907_J/noArg");
    private Path actualOutputPath = Paths.get("src/test/resources/actualOutput/06907_J/noArg");
    private String succeededProjectsListPath = "testResults/succeededProjects.txt";
    private Path failingOutputPathForCurrentRun;
    private String fullProjectName;
    private String failingOutputPath = "testResults/failing";
    private String archivePath = "/home/reza/testIfs/projects/BIC/archive";

    private String arg;

    @Before
    public void setUp() throws Exception {
        if (System.getProperty(PROJECT_PROPERTY) != null)
            project = System.getProperty(PROJECT_PROPERTY);
        if (System.getProperty(EXPECTED_OUTPUT_PROPERTY) != null)
            expectedOutputPath = Paths.get(System.getProperty(EXPECTED_OUTPUT_PROPERTY));
        if (System.getProperty(ACTUAL_OUTPUT_PROPERTY) != null)
            actualOutputPath = Paths.get(System.getProperty(ACTUAL_OUTPUT_PROPERTY));
        if (System.getProperty(SUCCEEDED_PROJECTS_LIST_PATH) != null)
            succeededProjectsListPath = System.getProperty(SUCCEEDED_PROJECTS_LIST_PATH);
        if (System.getProperty(FAILING_OUTPUT_PATH) != null)
            failingOutputPath = System.getProperty(FAILING_OUTPUT_PATH);
        arg = System.getProperty(ARG);
        isShiny = "-s".equals(arg) ? true : false;

        failingOutputPathForCurrentRun = Paths.get(String.format("%s/%s", failingOutputPath, DATE_AND_TIME_FORMAT.format(testStartTime)));
        fullProjectName = Utils.getFullProjectNameFromRequestId(project);
    }

    @Test
    public void whenRunningCreateManifestSheet_outputFilesShouldBeAsBefore() throws Exception {
        LOGGER.info(String.format("Running test for project: %s, expected path: %s, actual path: %s", project, expectedOutputPath, actualOutputPath));

        assertOutputFiles();
    }

    private void assertOutputFiles() throws Exception {
        assertOutputFilesContent();
        if (!isShiny)
            assertArchiveFiles(getProjectOutputPath(actualOutputPath));
        else
            LOGGER.info("Omitting comparing archive files with actual for Shiny project");
    }

    private void assertOutputFilesContent() throws Exception {
        BiPredicate<String, String> areLinesEqualExceptPathsAndDatesPredicate = new LinesEqualExceptPathsAndDatesPredicate(actualOutputPath, expectedOutputPath);
        FolderComparator folderComparator = new FileContentFolderComparator.Builder()
                .setAreLinesEqualPredicate(areLinesEqualExceptPathsAndDatesPredicate)
                .build();

        folderComparator.registerFailingComparisonListener(new ActualOutputFailingTestListener(failingOutputPathForCurrentRun, project));
        folderComparator.registerFailingComparisonListener(new ExpectedOutputFailingTestListener(failingOutputPathForCurrentRun, project));
        folderComparator.registerFailingComparisonListener(new RunInfoFailingTestListener(actualOutputPath, failingOutputPathForCurrentRun));

        folderComparator.registerFailingNumberOfFilesListener(new FailingNumberOfFilesListener(failingOutputPathForCurrentRun));
        folderComparator.registerFailingNumberOfFilesListener(new RunInfoFailingTestListener(actualOutputPath, failingOutputPathForCurrentRun));

        LOGGER.info(String.format("Comparing actual dir: %s and expected dir: %s", actualOutputPath, expectedOutputPath));

        assertThat(folderComparator.compare(actualOutputPath, expectedOutputPath), is(true));
    }

    private void assertArchiveFiles(Path actualOutputPathForProject) throws Exception {
        if (shouldCompareArchiveFiles(actualOutputPathForProject)) {
            Path archiveProjectTodayPath = getArchiveProjectTodayPath();
            assertThat(pathExists(archiveProjectTodayPath), is(true));

            FolderComparator folderComparator = new FileContentFolderComparator.Builder()
                    .setShouldCompareLogFile(() -> false)
                    .build();

            folderComparator.registerFailingComparisonListener(new ArchiveOutputFailingTestListener(failingOutputPathForCurrentRun, project));
            folderComparator.registerFailingComparisonListener(new ActualOutputFailingTestListener(failingOutputPathForCurrentRun, project));
            folderComparator.registerFailingComparisonListener(new RunInfoFailingTestListener(actualOutputPath, failingOutputPathForCurrentRun));

            folderComparator.registerFailingNumberOfFilesListener(new FailingNumberOfFilesListener(failingOutputPathForCurrentRun));
            folderComparator.registerFailingNumberOfFilesListener(new RunInfoFailingTestListener(actualOutputPath, failingOutputPathForCurrentRun));

            LOGGER.info(String.format("Comparing actual dir: %s and archive dir: %s", actualOutputPathForProject, archiveProjectTodayPath));

            assertThat(folderComparator.compare(actualOutputPathForProject, archiveProjectTodayPath), is(true));
        }
    }

    private boolean pathExists(Path path) {
        if (path.toFile().exists())
            return true;

        LOGGER.error(String.format("Path: %s does not exist.", path));
        return false;
    }

    private boolean shouldCompareArchiveFiles(Path actualOutputPathForProject) {
        return actualOutputPathForProject.toFile().exists()
                && actualOutputPathForProject.toFile().listFiles(new NonLogFileFilter()).length > 0;
    }

    private Path getArchiveProjectTodayPath() {
        String archiveProjectPath = String.format("%s/%s", archivePath, fullProjectName);
        return Paths.get(String.format("%s/%s", archiveProjectPath, DATE_ARCHIVE_FORMAT.format(new Date())));
    }

    private Path getProjectOutputPath(Path path) {
        return Paths.get(String.format("%s/%s", path, Utils.getFullProjectNameFromRequestId(project)));
    }

    private class WriteToTestReportOnFailure extends TestWatcher {
        @Override
        protected void succeeded(Description description) {
            Path succeeded = Paths.get(succeededProjectsListPath);
            try {
                createFileIfNeeded(succeeded);
                List<String> succeededProject = Arrays.asList(String.format("%s%s", project, arg));
                Files.write(succeeded, succeededProject, StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new RuntimeException(String.format("Unable to create file: %s", succeeded.toString()), e);
            }
        }

        @Override
        protected void failed(Throwable e, Description description) {
            saveFailedProjectsListToFile();
        }

        private void saveFailedProjectsListToFile() {
            Path failedProjectsList = Paths.get(succeededProjectsListPath);
            try {
                createFileIfNeeded(failedProjectsList);
                writeToFile(failedProjectsList);
            } catch (IOException e1) {
                throw new RuntimeException(String.format("Unable to create file: %s", failedProjectsList), e1);
            }
        }

        private void writeToFile(Path failedProjectsList) throws IOException {
            List<String> failedInfo = Arrays.asList(project);
            Files.write(failedProjectsList, failedInfo, StandardOpenOption.APPEND);
        }

        private void createFileIfNeeded(Path path) throws IOException {
            if (!Files.exists(path))
                Files.createFile(path);
        }
    }
}