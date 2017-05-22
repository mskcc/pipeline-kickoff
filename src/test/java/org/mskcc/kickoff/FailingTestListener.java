package org.mskcc.kickoff;

import java.nio.file.Path;

interface FailingTestListener {
    void update(Path actualPath, Path expectedSubPath);
}
