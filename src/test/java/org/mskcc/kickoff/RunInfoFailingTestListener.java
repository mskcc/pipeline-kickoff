package org.mskcc.kickoff;

import org.mskcc.kickoff.util.Utils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RunInfoFailingTestListener implements FailingTestListener {
    private FileCopier fileCopier;
    private Path actualOutputPath;
    private Path failingOutputPathForCurrentRun;

    public RunInfoFailingTestListener(Path actualOutputPath, Path failingOutputPathForCurrentRun) {
        fileCopier = new FileCopier();
        this.actualOutputPath = actualOutputPath;
        this.failingOutputPathForCurrentRun = failingOutputPathForCurrentRun;
    }

    @Override
    public void update(Path actualSubPath, Path expectedSubPath) {
        fileCopier.copy(Paths.get(Utils.getRunInfoPath(actualOutputPath)), failingOutputPathForCurrentRun);
    }
}
