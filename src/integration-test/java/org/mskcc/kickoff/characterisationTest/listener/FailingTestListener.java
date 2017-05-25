package org.mskcc.kickoff.characterisationTest.listener;

import java.nio.file.Path;

public interface FailingTestListener {
    void update(Path actualPath, Path expectedSubPath);
}
