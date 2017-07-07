package org.mskcc.kickoff.characterisationTest.comparator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class FilesEqualWithoutLinesOrdering implements BiPredicate<File, File> {
    private static final Logger LOGGER = Logger.getLogger(FilesEqualWithoutLinesOrdering.class);
    private LinesComparator linesComparator;
    private BiPredicate<String, String> areLinesEqualPredicate;

    public FilesEqualWithoutLinesOrdering(BiPredicate<String, String> areLinesEqualPredicate) {
        this.areLinesEqualPredicate = areLinesEqualPredicate;
    }

    @Override
    public boolean test(File actualFile, File expectedFile) {
        LOGGER.info(String.format("Comparing two files without lines ordering: %s and %s", actualFile, expectedFile));
        try {
            List<String> actualLines = getAllNonEmptyLinesFromFile(actualFile);
            List<String> expectedLines = getAllNonEmptyLinesFromFile(expectedFile);

            BiPredicate<List<String>, List<String>> filesAreEqualPredicate = getFilesAreEqualPredicate(actualFile);
            linesComparator = new LinesComparator(areLinesEqualPredicate, filesAreEqualPredicate);
            return linesComparator.areEqual(actualLines, expectedLines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private BiPredicate<List<String>, List<String>> getFilesAreEqualPredicate(File actualFile) {
        if(FileContentFolderComparator.isLogFile(actualFile.toPath()))
            return getAdditionalLinesAllowedPredicate();
        return getEqualNumberOfLinesPredicate();
    }

    private BiPredicate<List<String>, List<String>> getAdditionalLinesAllowedPredicate() {
        return (additionalLines, missingLines) -> missingLines.size() == 0;
    }

    private BiPredicate<List<String>, List<String>> getEqualNumberOfLinesPredicate() {
        return (additionalLines, missingLines) -> additionalLines.size() == 0 && missingLines.size() == 0;
    }

    private List<String> getAllNonEmptyLinesFromFile(File file) throws IOException {
        return Files.readAllLines(file.toPath()).stream().filter(l -> !StringUtils.isEmpty(l)).collect(Collectors.toList());
    }

}
