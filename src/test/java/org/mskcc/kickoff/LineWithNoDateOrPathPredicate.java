package org.mskcc.kickoff;

import org.mskcc.kickoff.util.Constants;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class LineWithNoDateOrPathPredicate implements Predicate<String> {
    private final String actualOutputPath;
    private final String expectedOutputPath;

    public LineWithNoDateOrPathPredicate(String actualOutputPath, String expectedOutputPath) {
        this.actualOutputPath = actualOutputPath;
        this.expectedOutputPath = expectedOutputPath;
    }

    @Override
    public boolean test(String line) {
        Predicate<String> isLineWithProjectPathPredicate = getIsLineWithPathPredicate();
        Predicate<String> isLineWithDatePredicate = getIsLineWithDatePredicate();

        return !isLineWithProjectPathPredicate.test(line) && !isLineWithDatePredicate.test(line);
    }

    private Predicate<String> getIsLineWithDatePredicate() {
        Pattern pattern = Pattern.compile("^.*date.*$", Pattern.CASE_INSENSITIVE);
        return testLine -> pattern.matcher(testLine).matches();
    }

    private Predicate<String> getIsLineWithPathPredicate() {
        List<String> pathsToOmit = Arrays.asList(actualOutputPath, System.getProperty("user.dir"), expectedOutputPath);
        return testLine -> !testLine.startsWith(Constants.DESIGN_FILE) && pathsToOmit.stream().anyMatch(s -> testLine.contains(s));
    }
}
