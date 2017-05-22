package org.mskcc.kickoff.util;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class Utils {
    public static final String LOG_FILE_PREFIX = "Log_";
    public static final String SHINY = "shiny";
    private static boolean exitLater;
    private static ArrayList<String> log_messages = new ArrayList<>();
    private static final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");


    public static void print(String message) {
        if (message.startsWith("[")) {
            if (message.startsWith("[ERROR]")) {
                exitLater = true;
            }
            log_messages.add(message);
        }
        System.out.println(message);
    }

    public static boolean isExitLater() {
        return exitLater;
    }

    public static void setExitLater(boolean exitLater) {
        Utils.exitLater = exitLater;
    }

    public static ArrayList<String> getLog_messages() {
        return log_messages;
    }

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
}
