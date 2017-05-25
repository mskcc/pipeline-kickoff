package org.mskcc.kickoff.characterisationTest.listener;

import org.mskcc.kickoff.characterisationTest.FileCopier;
import org.mskcc.kickoff.characterisationTest.OutputType;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.nio.file.Path;

public class ArchiveOutputFailingTestListener implements FailingTestListener {
    private final Path failingOutputPath;

    public ArchiveOutputFailingTestListener(Path failingOutputPathForCurrentRun, String project) {
        failingOutputPath = Utils.getFailingOutputPathForType(failingOutputPathForCurrentRun, OutputType.ARCHIVE.getTypeName(), project);
    }

    @Override
    public void update(Path actualFailingPath, Path expectedFailingPath) {
        new File(failingOutputPath.toString()).mkdirs();
        FileCopier.copy(expectedFailingPath, failingOutputPath);
    }
}
