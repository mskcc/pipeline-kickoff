package org.mskcc.kickoff;

import org.mskcc.kickoff.util.Constants;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.function.Predicate;

class RelevantProjectFileFilter implements Predicate<Path> {
    @Override
    public boolean test(Path path) {
        return !Constants.RUN_INFO_PATH.equals(path);
    }
}
