package org.mskcc.kickoff.characterisationTest.comparator;

import org.mskcc.kickoff.util.Constants;

import java.nio.file.Path;
import java.util.function.Predicate;

public class RelevantProjectFileFilter implements Predicate<Path> {
    @Override
    public boolean test(Path path) {
        return !path.toString().endsWith(Constants.RUN_INFO_PATH);
    }
}
