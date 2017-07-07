package org.mskcc.kickoff.characterisationTest.comparator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Recipe;
import org.mskcc.kickoff.util.Constants;

import java.nio.file.Path;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinesEqualExceptPathsAndDatesPredicate implements BiPredicate<String, String> {
    private static final Logger LOGGER = Logger.getLogger(LinesEqualExceptPathsAndDatesPredicate.class);
    private final Path actualOutputPathForProject;
    private final Path expectedOutputPathForProject;

    public LinesEqualExceptPathsAndDatesPredicate(Path actualOutputPathForProject, Path expectedOutputPathForProject) {
        this.actualOutputPathForProject = actualOutputPathForProject;
        this.expectedOutputPathForProject = expectedOutputPathForProject;
    }

    @Override
    public boolean test(String actualLogLine, String expectedLogLine) {
        actualLogLine = getStringWithoutPaths(actualLogLine);
        expectedLogLine = getStringWithoutPaths(expectedLogLine);

        actualLogLine = getStringWithoutDates(actualLogLine);
        expectedLogLine = getStringWithoutDates(expectedLogLine);

        if (isUnsupportedRecipeLine(actualLogLine, expectedLogLine))
            return true;
        return actualLogLine.equals(expectedLogLine);
    }

    private boolean isUnsupportedRecipeLine(String actualLogLine, String expectedLogLine) {
        return StringUtils.containsIgnoreCase(actualLogLine, Constants.RECIPE)
                && StringUtils.containsIgnoreCase(expectedLogLine, Constants.RECIPE)
                && isUnsupportedRecipe(expectedLogLine);
    }

    private boolean isUnsupportedRecipe(String expectedLogLine) {
        Pattern pattern = Pattern.compile(".*Recipe: (.+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(expectedLogLine);

        if (matcher.find()) {
            String recipe = matcher.group(1);
            LOGGER.info(String.format("Recipe found in expected file: %s", recipe));
            try {
                Recipe.getRecipeByValue(recipe);
                return false;
            } catch (Recipe.UnsupportedRecipeException e) {
                LOGGER.info(String.format("Unsupported recipe found in expected file: %s. Omitting comparing it", recipe));
                return true;
            }
        }

        return false;
    }

    private String getStringWithoutDates(String line) {
        Pattern pattern = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
        Matcher matcher = pattern.matcher(line);
        if (matcher.find()) {
            LOGGER.info(String.format("Removing dates from line: %s", line));
            line = line.replaceAll(matcher.group(), "");
            LOGGER.info(String.format("Line after removing dates from line: %s", line));
        }

        return line;
    }

    private String getStringWithoutPaths(String line) {
        if(line.contains(actualOutputPathForProject.toString()) || line.contains(expectedOutputPathForProject.toString())) {
            LOGGER.info(String.format("Removing paths from line: %s", line));
            line = line.replaceAll(actualOutputPathForProject.toString(), "");
            line = line.replaceAll(expectedOutputPathForProject.toString(), "");
            LOGGER.info(String.format("Line after removing paths from line: %s", line));
        }

        return line;
    }
}
