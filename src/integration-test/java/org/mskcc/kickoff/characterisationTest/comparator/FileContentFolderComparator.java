package org.mskcc.kickoff.characterisationTest.comparator;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.characterisationTest.listener.FailingTestListener;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class FileContentFolderComparator implements FolderComparator {
    private static final Logger LOGGER = Logger.getLogger(FileContentFolderComparator.class);

    private final List<FailingTestListener> failingComparisonListeners = new ArrayList<>();
    private final List<FailingTestListener> failingNumberOfFilesListeners = new ArrayList<>();

    private final BiPredicate<File, File> areFilesEqualPredicate;
    private final XslxComparator xslxComparator;

    private FileContentFolderComparator(BiPredicate<String, String> areLinesEqualPredicate) {
        areFilesEqualPredicate = new FilesEqualWithoutLinesOrdering(areLinesEqualPredicate);
        xslxComparator = new XslxComparator(areLinesEqualPredicate);
    }

    public static String getShinyLogFileName() {
        return String.format("%s%s_%s.txt", Constants.LOG_FILE_PREFIX, Utils.LOG_DATE_FORMAT.format(new Date()), Utils.SHINY);
    }

    public static String getLogFileName() {
        return String.format("%s%s.txt", Constants.LOG_FILE_PREFIX, Utils.LOG_DATE_FORMAT.format(new Date()));
    }

    public static boolean isLogFile(Path path) {
        return path.getFileName().toString().startsWith(Constants.LOG_FILE_PREFIX);
    }

    @Override
    public boolean compare(Path actualPath, Path expectedPath) throws Exception {
        if (!isNumberOfFilesInDirsEqual(actualPath, expectedPath)) {
            for (FailingTestListener failingNumberOfFilesListener : failingNumberOfFilesListeners) {
                failingNumberOfFilesListener.update(actualPath, expectedPath);
            }

            return false;
        }
        try (DirectoryStream<Path> expectedDirStream = Files.newDirectoryStream(expectedPath)) {
            for (Path expectedSubPath : expectedDirStream) {
                if (!expectedFileExistsInActualDir(actualPath, expectedSubPath))
                    return false;

                Path actualSubPath = getActualSubPath(actualPath, expectedSubPath);
                if (isLogFolder(expectedSubPath)) {
                    LOGGER.info(String.format("Omitting comparing actual log files in path: %s with expected path: %s", actualSubPath, expectedSubPath));
                    continue;
                }

                if (!arePathsEqual(expectedSubPath, actualSubPath)) return false;
            }
        }

        return true;
    }

    @Override
    public void registerFailingComparisonListener(FailingTestListener failingTestListener) {
        failingComparisonListeners.add(failingTestListener);
    }

    @Override
    public void registerFailingNumberOfFilesListener(FailingTestListener failingTestListener) {
        failingNumberOfFilesListeners.add(failingTestListener);
    }

    private boolean arePathsEqual(Path expectedSubPath, Path actualSubPath) throws Exception {
        File expectedFile = expectedSubPath.toFile();

        if (expectedFile.isFile()) {
            if (!areFilesEqual(actualSubPath.toFile(), expectedFile)) {
                for (FailingTestListener failingTestListener : failingComparisonListeners)
                    failingTestListener.update(actualSubPath, expectedSubPath);
                return false;
            }
        } else if (!compare(actualSubPath, expectedSubPath))
            return false;
        return true;
    }

    private boolean expectedFileExistsInActualDir(Path actualPath, Path expectedSubPath) {
        if (!Files.exists(getActualSubPath(actualPath, expectedSubPath))) {
            LOGGER.error(String.format("Expected file: %s " +
                    "does not exist in test path: %s", expectedSubPath, actualPath));
            return false;
        }

        return true;
    }

    private Path getActualSubPath(Path actualPath, Path expectedSubPath) {
        String fileName = getActualFileName(expectedSubPath);
        return actualPath.resolve(fileName);
    }

    private String getActualFileName(Path expectedSubPath) {
        if (isLogFile(expectedSubPath))
            return getLogFileName(expectedSubPath);
        return expectedSubPath.getFileName().toString();
    }

    private String getLogFileName(Path expectedSubPath) {
        if (expectedSubPath.getFileName().toString().contains(Utils.SHINY))
            return getShinyLogFileName();
        return getLogFileName();
    }

    private boolean isNumberOfFilesInDirsEqual(Path actualPath, Path expectedPath) {
        LOGGER.info(String.format("Comparing number of files in directories: %s and %s", actualPath, expectedPath));

        File expectedFile = expectedPath.toFile();
        File actualFile = actualPath.toFile();

        int actualFileNumberOfFiles = getNumberOfRelevantProjectFiles(actualFile);
        int expectedFileNumberOfFiles = getNumberOfRelevantProjectFiles(expectedFile);

        LOGGER.info(String.format("Comparing number of files in directories: %s = %d and %s = %d", actualPath, actualFileNumberOfFiles, expectedPath, expectedFileNumberOfFiles));

        if (expectedFileNumberOfFiles != actualFileNumberOfFiles) {
            String actualFilesList = Utils.getFilesInDir(actualFile).stream().map(f -> f.toString() + "\n").collect(Collectors.joining());
            String expectedFilesList = Utils.getFilesInDir(expectedFile).stream().map(f -> f.toString() + "\n").collect(Collectors.joining());
            LOGGER.error(String.format("Number of files in directories: %s and %s is different!\nexpected (%d):\n%s\nactual(%d):\n%s",
                    expectedPath, actualPath, expectedFileNumberOfFiles, expectedFilesList, actualFileNumberOfFiles, actualFilesList));
            return false;
        }

        return true;
    }

    private int getNumberOfRelevantProjectFiles(File actualFile) {
        RelevantProjectFileFilter filter = new RelevantProjectFileFilter();
        return Utils.getFilesInDir(actualFile, filter).size();
    }

    private boolean isLogFolder(Path directoryFile) {
        return directoryFile.toFile().getName().equals(Constants.LOG_FILE_PATH);
    }

    private boolean areFilesEqual(File actualFile, File expectedFile) throws Exception {
        if (isXlsxFile(actualFile))
            return areXslxFilesEqualWithoutLineOrder(actualFile, expectedFile);
        return compareTxtFiles(actualFile, expectedFile);
    }

    private boolean compareTxtFiles(File actualFile, File expectedFile) {
        if (actualFile.getPath().contains("grouping")) {
            LOGGER.info(String.format("Ommiting comparing grouping file: %s", actualFile));
            return true;
        }

        return areFilesEqualPredicate.test(actualFile, expectedFile);
    }

    private boolean areXslxFilesEqualWithoutLineOrder(File actualFile, File expectedFile) throws IOException {
        LOGGER.info(String.format("Comparing two xlsx files: %s and %s", actualFile, expectedFile));
        return xslxComparator.compareWithoutLinesOrdering(actualFile, expectedFile);
    }

    private boolean isXlsxFile(File testFile) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.xlsx");
        return pathMatcher.matches(Paths.get(testFile.getName()));
    }

    public static class Builder {
        private BiPredicate<String, String> areLinesEqualPredicate = String::equals;

        public Builder setAreLinesEqualPredicate(BiPredicate<String, String> areLinesEqualPredicate) {
            this.areLinesEqualPredicate = areLinesEqualPredicate;
            return this;
        }

        public Builder setShouldCompareLogFile(BooleanSupplier shouldCompareLogFile) {
            return this;
        }

        public FileContentFolderComparator build() {
            return new FileContentFolderComparator(areLinesEqualPredicate);
        }
    }
}
