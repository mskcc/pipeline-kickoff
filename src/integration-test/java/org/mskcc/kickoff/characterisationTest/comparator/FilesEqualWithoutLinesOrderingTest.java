package org.mskcc.kickoff.characterisationTest.comparator;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class FilesEqualWithoutLinesOrderingTest {
    private FilesEqualWithoutLinesOrdering filesEqualWithoutLinesOrdering;

    @Before
    public void setUp() throws Exception {
        Path actualPath = Paths.get("/home/reza/test/actual/02756_B/noArg/");
        Path expectedPath = Paths.get("/home/reza/test/expectedOutput/02756_B/noArg/");
        LinesEqualExceptPathsAndDatesPredicate areLinesEqualExceptPathsAndDatesPredicate = new LinesEqualExceptPathsAndDatesPredicate(actualPath, expectedPath);

        filesEqualWithoutLinesOrdering = new FilesEqualWithoutLinesOrdering(areLinesEqualExceptPathsAndDatesPredicate);
    }

    @Test
    public void whenFilesAreEqual_shouldReturnTrue() {
        File actualFile = new File("src/integration-test/resources/actualOutput/actualMatchingExpected.txt");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.txt");
        boolean areFilesEqual = filesEqualWithoutLinesOrdering.test(actualFile, expectedFile);

        assertThat(areFilesEqual, is(true));
    }

    @Test
    public void whenFilesNotAreEqual_shouldReturnFalse() {
        File actualFile = new File("src/integration-test/resources/actualOutput/actualNotMatchingExpected.txt");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.txt");
        boolean areFilesEqual = filesEqualWithoutLinesOrdering.test(actualFile, expectedFile);

        assertThat(areFilesEqual, is(false));
    }

    @Test
    public void whenActualFileHasAdditionalLines_shouldReturnFalse() {
        File actualFile = new File("src/integration-test/resources/actualOutput/actualWithAdditionalLines.txt");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.txt");
        boolean areFilesEqual = filesEqualWithoutLinesOrdering.test(actualFile, expectedFile);

        assertThat(areFilesEqual, is(false));
    }

    @Test
    public void whenActualHasMissingLines_shouldReturnFalse() {
        File actualFile = new File("src/integration-test/resources/actualOutput/actualWithMissingLines.txt");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.txt");
        boolean areFilesEqual = filesEqualWithoutLinesOrdering.test(actualFile, expectedFile);

        assertThat(areFilesEqual, is(false));
    }

}