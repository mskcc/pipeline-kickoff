package org.mskcc.kickoff;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FailingNumberOfFilesListener implements FailingTestListener {
    private static final String FAILED_NUMBER_OF_FILES_TXT = "wrongNumberOfFiles.txt";
    private Path failingOutputPathForCurrentRun;

    public FailingNumberOfFilesListener(Path failingOutputPathForCurrentRun) {
        this.failingOutputPathForCurrentRun = failingOutputPathForCurrentRun;
    }

    @Override
    public void update(Path actualPath, Path expectedSubPath) {
        copyNotEqualFilesListToReport(actualPath, expectedSubPath);
    }

    private void copyNotEqualFilesListToReport(Path actualPath, Path expectedPath) {
        Path expectedAndActualFilesList = null;

        try {
            createFailingOutputPathForCurrentRunIfNeeded();

            expectedAndActualFilesList = Paths.get(String.format("%s/%s", failingOutputPathForCurrentRun, FAILED_NUMBER_OF_FILES_TXT));
            Files.createFile(expectedAndActualFilesList);
            List<String> expectedFilesList = Arrays.stream(expectedPath.toFile().listFiles()).map(f -> f.getPath()).collect(Collectors.toList());
            List<String> actualFilesList = Arrays.stream(actualPath.toFile().listFiles()).map(f -> f.getPath()).collect(Collectors.toList());

            List<String> dirFiles = new ArrayList<>(Arrays.asList("expected files:"));
            dirFiles.addAll(expectedFilesList);
            dirFiles.add("actual files:");
            dirFiles.addAll(actualFilesList);

            Files.write(expectedAndActualFilesList, dirFiles);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to create file with list of files: %s", expectedAndActualFilesList), e);
        }
    }

    private void createFailingOutputPathForCurrentRunIfNeeded() {
        new File(failingOutputPathForCurrentRun.toString()).mkdirs();
    }
}
