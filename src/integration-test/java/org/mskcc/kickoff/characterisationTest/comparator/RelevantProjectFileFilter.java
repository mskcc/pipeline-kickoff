package org.mskcc.kickoff.characterisationTest.comparator;

import java.nio.file.Path;
import java.util.function.Predicate;

public class RelevantProjectFileFilter implements Predicate<Path> {
    @Override
    public boolean test(Path path) {
        return !path.toString().contains("c_to_p");
    }
}
