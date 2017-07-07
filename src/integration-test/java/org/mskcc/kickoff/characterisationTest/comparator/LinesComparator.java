package org.mskcc.kickoff.characterisationTest.comparator;

import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

public class LinesComparator {
    private static final Logger LOGGER = Logger.getLogger(LinesComparator.class);

    private final BiPredicate<String, String> areLinesEqualPredicate;
    private BiPredicate<List<String>, List<String>> filesAreEqualPredicate;

    public LinesComparator(BiPredicate<String, String> areLinesEqualPredicate, BiPredicate<List<String>, List<String>> filesAreEqualPredicate) {
        this.areLinesEqualPredicate = areLinesEqualPredicate;
        this.filesAreEqualPredicate = filesAreEqualPredicate;
    }

    public LinesComparator(BiPredicate<String, String> areLinesEqualPredicate) {
        this(areLinesEqualPredicate, (additionalLines, missingLines) -> additionalLines.size() == 0 && missingLines.size() == 0);
    }

    public boolean areEqual(List<String> actualLines, List<String> expectedLines) {
        List<String> actualLinesCopy = new ArrayList<>(actualLines);
        List<String> missingLines = new ArrayList<>();

        for (String expectedLine : expectedLines) {
            LOGGER.info(String.format("Looking for expected line: %s in actual file", expectedLine));
            boolean expectedLineExists = false;
            for (String actualLine : actualLines) {
                if (areLinesEqualPredicate.test(actualLine, expectedLine)) {
                    expectedLineExists = true;
                    actualLinesCopy.remove(actualLine);
                    break;
                }
            }

            if (!expectedLineExists) {
                LOGGER.error(String.format("Expected line: %s is not present in actual file", expectedLine));
                missingLines.add(expectedLine);
            }
        }

        for (String actualLine : actualLinesCopy) {
            LOGGER.error(String.format("Additional not expected line in actual file: %s", actualLine));
        }

        for (String missingLine : missingLines) {
            LOGGER.error(String.format("Missing line: %s is missing in actual file", missingLine));
        }

        return filesAreEqualPredicate.test(actualLinesCopy, missingLines);
    }
}
