package org.mskcc.kickoff;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class LinesEqualExceptPathsAndDatesPredicateTest {
    private LinesEqualExceptPathsAndDatesPredicate linesEqualExceptPathsAndDatesPredicate;
    private final Path expectedPath = Paths.get("expected/path/project");
    private final Path actualPath = Paths.get("other/path/actual");

    @Before
    public void setUp() throws Exception {
        linesEqualExceptPathsAndDatesPredicate = new LinesEqualExceptPathsAndDatesPredicate(actualPath, expectedPath);
    }

    @Test
    public void whenLinesEqual_shouldReturnTrue() {
        String actualLine = "[INFO]";
        String expectedLine = "[INFO]";

        boolean areEqual = linesEqualExceptPathsAndDatesPredicate.test(actualLine, expectedLine);

        Assert.assertTrue(areEqual);
    }

    @Test
    public void whenLinesEqualExceptFromSimplePaths_shouldReturnTrue() {
        String actualLine = String.format("[INFO] /%s", actualPath);
        String expectedLine = String.format("[INFO] /%s", expectedPath);

        boolean areEqual = linesEqualExceptPathsAndDatesPredicate.test(actualLine, expectedLine);

        Assert.assertTrue(areEqual);
    }

    @Test
    public void whenLinesEqualExceptFromPathsBothWithTilda_shouldReturnTrue() {
        String actualLine = String.format("[WARNING] ~/%s", actualPath);
        String expectedLine = String.format("[WARNING] ~/%s", expectedPath);

        boolean areEqual = linesEqualExceptPathsAndDatesPredicate.test(actualLine, expectedLine);

        Assert.assertTrue(areEqual);
    }

    @Test
    public void whenLinesEqualExceptFromMultiPaths_shouldReturnTrue() {
        String actualLine = String.format("[INFO] /%s/the/same", actualPath);
        String expectedLine = String.format("[INFO] /%s/the/same", expectedPath);

        boolean areEqual = linesEqualExceptPathsAndDatesPredicate.test(actualLine, expectedLine);

        Assert.assertTrue(areEqual);
    }

    @Test
    public void whenLinesEqualExceptFromPathsEndingWithSlash_shouldReturnTrue() {
        String actualLine = String.format("[INFO] the/same/%s/", actualPath);
        String expectedLine = String.format("[INFO] the/same/%s/", expectedPath);

        boolean areEqual = linesEqualExceptPathsAndDatesPredicate.test(actualLine, expectedLine);

        Assert.assertTrue(areEqual);
    }

    @Test
    public void whenLinesEqualExceptFromPathsEndingWithOtherString_shouldReturnTrue() {
        String actualLine = String.format("[INFO] The Same log message  ~/same/%s/other/same Same message", actualPath);
        String expectedLine = String.format("[INFO] The Same log message  ~/same/%s/other/same Same message", expectedPath);

        boolean areEqual = linesEqualExceptPathsAndDatesPredicate.test(actualLine, expectedLine);

        Assert.assertTrue(areEqual);
    }
}