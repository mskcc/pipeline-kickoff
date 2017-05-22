package org.mskcc.kickoff;

import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.nio.file.Path;

public class ExpectedOutputFailingTestListener implements FailingTestListener {
    private final Path failingOutputPath;

    public ExpectedOutputFailingTestListener(Path failingOutputPathForCurrentRun, String project) {
        failingOutputPath = Utils.getFailingOutputPathForType(failingOutputPathForCurrentRun, OutputType.EXPECTED.getTypeName(), project);
    }

    @Override
    public void update(Path actualFailingPath, Path expectedFailingPath) {
        new File(failingOutputPath.toString()).mkdirs();
        FileCopier.copy(expectedFailingPath, failingOutputPath);
    }
}
