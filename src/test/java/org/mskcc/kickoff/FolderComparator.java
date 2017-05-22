package org.mskcc.kickoff;

import java.nio.file.Path;

interface FolderComparator {
    boolean compare(Path actualPath, Path expectedPath) throws Exception;

    void registerFailingComparisonListener(FailingTestListener failingTestListener);
    void registerFailingNumberOfFilesListener(FailingTestListener failingTestListener);
}
