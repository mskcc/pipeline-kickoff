package org.mskcc.kickoff.characterisationTest.listener;

import org.mskcc.kickoff.characterisationTest.FileCopier;
import org.mskcc.kickoff.characterisationTest.OutputType;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.nio.file.Path;

public class ActualOutputFailingTestListener implements FailingTestListener {
    private final Path failingOutputPath;

    public ActualOutputFailingTestListener(Path failingOutputPathForCurrentRun, String project) {
        failingOutputPath = Utils.getFailingOutputPathForType(failingOutputPathForCurrentRun, OutputType.ACTUAL.getTypeName(), project);
    }

    @Override
    public void update(Path actualFailingPath, Path expectedFailingPath) {
        new File(failingOutputPath.toString()).mkdirs();
        FileCopier.copy(actualFailingPath, failingOutputPath);
    }
}
