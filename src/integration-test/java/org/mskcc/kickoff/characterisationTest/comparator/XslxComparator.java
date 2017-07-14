package org.mskcc.kickoff.characterisationTest.comparator;

import org.apache.log4j.Logger;
import org.apache.poi.xssf.extractor.XSSFExcelExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

public class XslxComparator {
    private static final Logger LOGGER = Logger.getLogger(XslxComparator.class);

    private BiPredicate<String, String> areLinesEqualPredicate;

    public XslxComparator(BiPredicate<String, String> areLinesEqualPredicate) {
        this.areLinesEqualPredicate = areLinesEqualPredicate;
    }

    public boolean compareWithoutLinesOrdering(File actualFile, File expectedFile) throws IOException {
        List<String> actualLines = Arrays.asList(xslxToText(actualFile).split("\n"));
        List<String> expectedLines = Arrays.asList(xslxToText(expectedFile).split("\n"));

        LinesComparator linesComparator = new LinesComparator(areLinesEqualPredicate);
        return linesComparator.areEqual(actualLines, expectedLines);
    }

    private String xslxToText(File actualFile) throws IOException {
        return new XSSFExcelExtractor(new XSSFWorkbook(new FileInputStream(actualFile))).getText();
    }

    public boolean compare(File actualFile, File expectedFile) throws IOException {
        String actualFileText = xslxToText(actualFile);
        String expectedFileText = xslxToText(expectedFile);

        if (!actualFileText.equals(expectedFileText)) {
            LOGGER.error(String.format("Xlsx files: %s and %s are not equal", actualFile, expectedFile));
            return false;
        }

        return true;
    }
}
