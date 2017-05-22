package org.mskcc.kickoff.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Utils {
    public static final String LOG_FILE_PREFIX = "Log_";
    public static final String SHINY = "shiny";
    private static final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");

    public static String getRunInfoPath(Path dir) {
        return String.format("%s/%s", dir, Constants.RUN_INFO_PATH);
    }

    public static String getFullProjectNameFromRequestId(String requestId) {
        return String.format("%s%s", Constants.PROJECT_PREFIX, requestId);
    }

    public static Path getFailingOutputPathForType(Path failingOutputPathForCurrentRun, String outputType, String project) {
        return Paths.get(String.format("%s/%s/%s", failingOutputPathForCurrentRun, outputType, getFullProjectNameFromRequestId(project)));
    }

    public static String getLogFileName() {
        return LOG_FILE_PREFIX + dateFormat.format(new Date()) + ".txt";
    }

    public static String getShinyLogFileName() {
        return LOG_FILE_PREFIX + dateFormat.format(new Date()) + "_" + SHINY + ".txt";
    }

    public static List<File> getFilesInDir(File file, Predicate<? super Path> filter) {
        return getFilesInDir(file.toPath(), filter);
    }

    public static List<File> getFilesInDir(File file) {
        return getFilesInDir(file, path -> true);
    }

    public static List<File> getFilesInDir(Path path) {
        return getFilesInDir(path.toFile(), p -> true);
    }

    public static List<File> getFilesInDir(Path path, Predicate<? super Path> filter) {
        List<File> files = new ArrayList<>();
        try {
            files = Files.list(path)
                    .filter(filter)
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            //@TODO logger.warn
        }
        return files;
    }
}
