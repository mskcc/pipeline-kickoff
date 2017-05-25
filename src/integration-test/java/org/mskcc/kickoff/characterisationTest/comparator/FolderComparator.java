package org.mskcc.kickoff.characterisationTest.comparator;

import org.mskcc.kickoff.characterisationTest.listener.FailingTestListener;

import java.nio.file.Path;

public interface FolderComparator {
    boolean compare(Path actualPath, Path expectedPath) throws Exception;

    void registerFailingComparisonListener(FailingTestListener failingTestListener);
    void registerFailingNumberOfFilesListener(FailingTestListener failingTestListener);
}
