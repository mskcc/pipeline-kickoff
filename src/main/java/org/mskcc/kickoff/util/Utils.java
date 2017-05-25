package org.mskcc.kickoff.util;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.Arguments;

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
    public static final String SHINY = "shiny";
    public static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("dd-MM-yy");
    public static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    public static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    public static boolean exitLater;
    private static String devLogFileName = "pipeline_kickoff";
    private static String shinyDevLogFileName = String.format("%s_%s", devLogFileName, SHINY);

    public static String getRunInfoPath(Path dir) {
        return String.format("%s/%s", dir, Constants.RUN_INFO_PATH);
    }

    public static Path getFailingOutputPathForType(Path failingOutputPathForCurrentRun, String outputType, String project) {
        return Paths.get(String.format("%s/%s/%s", failingOutputPathForCurrentRun, outputType, getFullProjectNameWithPrefix(project)));
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

    public static String getFullProjectNameWithPrefix(String requestID) {
        return String.format("%s%s", Constants.PROJECT_PREFIX, requestID);
    }

    public static String getFullOutputProjectPath() {
        return String.format("%s/%s", Arguments.outdir, getFullProjectNameWithPrefix(Arguments.project));
    }

    public static String getPmLogFileName() {
        return String.format("%s%s.txt", Constants.LOG_FILE_PREFIX, LOG_DATE_FORMAT.format(new Date()));
    }

    public static String getDevLogFileName() {
        return String.format("logs/%s.log", Arguments.shiny ? shinyDevLogFileName : devLogFileName);
    }
}
