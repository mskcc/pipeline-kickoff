package org.mskcc.kickoff;

import org.apache.log4j.Logger;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class FileContentFolderComparator implements FolderComparator {
    private static final Logger LOGGER = Logger.getLogger(FileContentFolderComparator.class);
    private static final String LOGS_FOLDER = "logs";

    private final List<FailingTestListener> failingComparisonListeners = new ArrayList<>();
    private final List<FailingTestListener> failingNumberOfFilesListeners = new ArrayList<>();

    private final BiPredicate<String, String> areLinesEqualPredicate;
    private final BooleanSupplier shouldCompareLogFilePredicate;

    private FileContentFolderComparator(BiPredicate<String, String> areLinesEqualPredicate, BooleanSupplier shouldCompareLogFilePredicate) {
        this.areLinesEqualPredicate = areLinesEqualPredicate;
        this.shouldCompareLogFilePredicate = shouldCompareLogFilePredicate;
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
                if (isLogFolder(expectedSubPath) && !shouldCompareLogFilePredicate.getAsBoolean()) {
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
            LOGGER.error(String.format("Expected file: %s does not exist in test path: %s", expectedSubPath, actualPath));
            return false;
        }

        return true;
    }

    private Path getActualSubPath(Path actualPath, Path expectedSubPath) {
        String fileName = getActualFileName(expectedSubPath);
        return actualPath.resolve(fileName);
    }

    private String getActualFileName(Path expectedSubPath) {
        if(isLogFile(expectedSubPath))
            return getLogFileName(expectedSubPath);
        return expectedSubPath.getFileName().toString();
    }

    private String getLogFileName(Path expectedSubPath) {
        if(expectedSubPath.getFileName().toString().contains(Utils.SHINY))
            return Utils.getShinyLogFileName();
        return Utils.getLogFileName();
    }

    private boolean isLogFile(Path expectedSubPath) {
        return expectedSubPath.getFileName().toString().startsWith(Utils.LOG_FILE_PREFIX);
    }

    private boolean isNumberOfFilesInDirsEqual(Path actualPath, Path expectedPath) {
        LOGGER.info(String.format("Comparing number of files in directories: %s and %s", actualPath, expectedPath));

        File expectedFile = expectedPath.toFile();
        File actualFile = actualPath.toFile();

        int actualFileNumberOfFiles = getNumberOfRelevantProjectFiles(actualFile);
        int expectedFileNumberOfFiles = expectedFile.listFiles().length;

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
        return directoryFile.toFile().getName().equals(LOGS_FOLDER);
    }

    private boolean areFilesEqual(File actualFile, File expectedFile) throws Exception {
        if (isXlsxFile(actualFile))
            return areXslxFilesEqual(actualFile, expectedFile);
        return areFilesEqualExcludingPathsAndDates(actualFile, expectedFile);
    }

    private boolean areFilesEqualExcludingPathsAndDates(File actualFile, File expectedFile) throws IOException {
        LOGGER.info(String.format("Comparing two files: %s and %s", actualFile, expectedFile));
        List<String> actualLines = Files.readAllLines(actualFile.toPath());
        List<String> expectedLines = Files.readAllLines(expectedFile.toPath());

        for (int i = 0; i < expectedLines.size(); i++) {
            String expectedLine = expectedLines.get(i);
            String actualLine = actualLines.get(i);

            if (!areLinesEqualPredicate.test(actualLine, expectedLine)) {
                LOGGER.error(String.format("Different lines found while comparing files: expected file: \nline: %s <--- Expected from file: %s\nline: %s <--- Actual from file: %s", expectedLine, expectedFile, actualLine, actualFile));
                return false;
            }
        }

        return true;
    }

    private boolean isXlsxFile(File testFile) {
        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:*.xlsx");
        return pathMatcher.matches(Paths.get(testFile.getName()));
    }

    private boolean areXslxFilesEqual(File testFile, File expectedFile) throws IOException {
        LOGGER.info(String.format("Comparing two xlsx files: %s and %s", testFile, expectedFile));

        String testFileText = xslxToText(testFile);
        String expectedFileText = xslxToText(expectedFile);

        if (!testFileText.equals(expectedFileText)) {
            LOGGER.error(String.format("Xlsx files: %s and %s are not equal", testFile, expectedFile));
            return false;
        }

        return true;
    }

    private String xslxToText(File testFile) throws IOException {
        return new XSSFExcelExtractor(new XSSFWorkbook(new FileInputStream(testFile))).getText();
    }

    public static class Builder {
        private BiPredicate<String, String> areLinesEqualPredicate = String::equals;
        private BooleanSupplier shouldCompareLogFile = () -> true;

        public Builder setAreLinesEqualPredicate(BiPredicate<String, String> areLinesEqualPredicate) {
            this.areLinesEqualPredicate = areLinesEqualPredicate;
            return this;
        }

        public Builder setShouldCompareLogFile(BooleanSupplier shouldCompareLogFile) {
            this.shouldCompareLogFile = shouldCompareLogFile;
            return this;
        }

        public FileContentFolderComparator build() {
            return new FileContentFolderComparator(areLinesEqualPredicate, shouldCompareLogFile);
        }
    }
}
