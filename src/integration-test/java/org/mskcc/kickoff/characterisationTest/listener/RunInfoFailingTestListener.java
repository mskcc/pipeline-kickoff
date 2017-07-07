package org.mskcc.kickoff.characterisationTest.listener;

import org.mskcc.kickoff.characterisationTest.FileCopier;
import org.mskcc.kickoff.util.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RunInfoFailingTestListener implements FailingTestListener {
    private final Path actualOutputPath;
    private final Path failingOutputPathForCurrentRun;

    public RunInfoFailingTestListener(Path actualOutputPath, Path failingOutputPathForCurrentRun) {
        this.actualOutputPath = actualOutputPath;
        this.failingOutputPathForCurrentRun = failingOutputPathForCurrentRun;
    }

    @Override
    public void update(Path actualSubPath, Path expectedSubPath) {
        FileCopier.copy(Paths.get(Utils.getRunInfoPath(actualOutputPath)), failingOutputPathForCurrentRun);
    }
}
