package org.mskcc.kickoff.listener;

import java.nio.file.Path;

public interface FailingTestListener {
    void update(Path actualPath, Path expectedSubPath);
}
