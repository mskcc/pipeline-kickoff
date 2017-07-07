package org.mskcc.kickoff.characterisationTest.comparator;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class XslxComparatorTest {
    @Test
    public void whenXlsxFilesAreEqual_shouldReturnTrue() throws IOException {
        XslxComparator xslxComparator = new XslxComparator((l1, l2) -> l1.equals(l2));

        File actualFile = new File("src/integration-test/resources/actualOutput/actualMatchingExpected.xlsx");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.xlsx");
        boolean filesAreEqual = xslxComparator.compareWithoutLinesOrdering(actualFile, expectedFile);

        assertThat(filesAreEqual, is(true));
    }

    @Test
    public void whenXlsxFilesAreNotEqual_shouldReturnFalse() throws IOException {
        XslxComparator xslxComparator = new XslxComparator((l1, l2) -> l1.equals(l2));

        File actualFile = new File("src/integration-test/resources/actualOutput/actualWithDifferentValueInCell.xlsx");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.xlsx");
        boolean filesAreEqual = xslxComparator.compareWithoutLinesOrdering(actualFile, expectedFile);

        assertThat(filesAreEqual, is(false));
    }

    @Test
    public void whenActualFileHasAdditionalLines_shouldReturnFalse() throws IOException {
        XslxComparator xslxComparator = new XslxComparator((l1, l2) -> l1.equals(l2));

        File actualFile = new File("src/integration-test/resources/actualOutput/actualWithAdditionalLines.xlsx");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.xlsx");
        boolean filesAreEqual = xslxComparator.compareWithoutLinesOrdering(actualFile, expectedFile);

        assertThat(filesAreEqual, is(false));
    }

    @Test
    public void whenActualFileIsMissingLines_shouldReturnFalse() throws IOException {
        XslxComparator xslxComparator = new XslxComparator((l1, l2) -> l1.equals(l2));

        File actualFile = new File("src/integration-test/resources/actualOutput/actualWithMissingLines.xlsx");
        File expectedFile = new File("src/integration-test/resources/expectedOutput/expected.xlsx");
        boolean filesAreEqual = xslxComparator.compareWithoutLinesOrdering(actualFile, expectedFile);

        assertThat(filesAreEqual, is(false));
    }

}