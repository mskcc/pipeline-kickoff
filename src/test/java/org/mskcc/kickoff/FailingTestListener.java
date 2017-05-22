package org.mskcc.kickoff;

import java.nio.file.Path;

public interface FailingTestListener {
    void update(Path actualPath, Path expectedSubPath);
}
