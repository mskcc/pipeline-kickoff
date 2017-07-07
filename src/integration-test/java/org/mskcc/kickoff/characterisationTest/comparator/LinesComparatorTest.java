package org.mskcc.kickoff.characterisationTest.comparator;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.is;

public class LinesComparatorTest {
    private LinesEqualExceptPathsAndDatesPredicate areLinesEqualExceptPathsAndDatesPredicate;
    private final Path actualPath = Paths.get("src/integration-test/resources/actualOutput/actualRequest.txt");
    private final Path expectedPath = Paths.get("src/integration-test/resources/expectedOutput/expectedRequest.txt");

    private final Path actualOutputPathForProject = Paths.get("/home/reza/work/test/actualOutput/06912_B/noArg");
    private final Path expectedOutputPathForProject = Paths.get("/home/reza/work/test/expectedOutput/06912_B/noArg");

    @Before
    public void setUp() throws Exception {
        areLinesEqualExceptPathsAndDatesPredicate = new LinesEqualExceptPathsAndDatesPredicate(actualOutputPathForProject, expectedOutputPathForProject);
    }

    @Test
    public void whenFilesAreEqual_shouldReturnTrue() throws Exception {
        LinesComparator linesComparator = new LinesComparator(areLinesEqualExceptPathsAndDatesPredicate);
        List<String> actualLines = Files.readAllLines(actualPath).stream().collect(Collectors.toList());
        List<String> expectedLines = Files.readAllLines(expectedPath).stream().collect(Collectors.toList());

        assertThat(linesComparator.areEqual(actualLines, expectedLines), is(true));
    }

}