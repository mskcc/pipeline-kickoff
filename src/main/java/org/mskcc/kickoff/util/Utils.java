package org.mskcc.kickoff.util;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.mskcc.domain.sample.Sample;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public class Utils {
    public static final String SHINY = "shiny";
    public static final DateFormat LOG_DATE_FORMAT = new SimpleDateFormat("dd-MM-yy");
    public static final String DEFAULT_DELIMITER = ",";

    private static boolean exitLater;

    public static String getRunInfoPath(Path dir) {
        return String.format("%s/%s", dir, Constants.RUN_INFO_PATH);
    }

    public static Path getFailingOutputPathForType(Path failingOutputPathForCurrentRun, String outputType, String project) {
        return Paths.get(String.format("%s/%s/%s", failingOutputPathForCurrentRun, outputType, getFullProjectNameWithPrefix(project)));
    }

    public static boolean isExitLater() {
        return exitLater;
    }

    public static void setExitLater(boolean exitLater) {
        Utils.exitLater = exitLater;
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

    public static String getPmLogFileName() {
        return String.format("%s%s.txt", Constants.LOG_FILE_PREFIX, LOG_DATE_FORMAT.format(new Date()));
    }

    public static String sampleNormalization(String sample) {
        sample = sample.replace("-", "_");
        if (!sample.equals(Constants.NA_LOWER_CASE)) {
            sample = "s_" + sample;
        }
        return sample;
    }

    public static String filterToAscii(String highUnicode) {
        String lettersAdded = highUnicode.replaceAll("ß", "ss").replaceAll("æ", "ae").replaceAll("Æ", "Ae");
        return Normalizer.normalize(lettersAdded, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }

    public static <T> String getJoinedCollection(Collection<T> collection) {
        return collection.stream().map(Object::toString).collect(Collectors.joining(DEFAULT_DELIMITER));
    }

    public static <T> String getJoinedCollection(Collection<T> collection, String delimiter) {
        return collection.stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }

    public static XSSFSheet addRowToSheet(XSSFWorkbook wb, XSSFSheet sheet, ArrayList<String> list, int rowNum, String type) {
        try {
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum = 0;
            for (String val : list) {
                if (val == null || val.isEmpty()) {
                    val = "#empty";
                }
                XSSFCell cell = row.createCell(cellNum++);
                XSSFCellStyle style = wb.createCellStyle();
                XSSFFont headerFont = wb.createFont();

                if (type != null) {
                    if (type.equals("header")) {
                        headerFont.setBold(true);
                        style.setFont(headerFont);
                    }
                    if (type.equals("instructions")) {
                        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 6));
                        style.setWrapText(true);
                        row.setRowStyle(style);
                        int lines = 2;
                        int pos = 0;
                        while ((pos = val.indexOf("\n", pos) + 1) != 0) {
                            lines++;
                        }
                        row.setHeight((short) (row.getHeight() * lines));
                    }
                    if (type.equals("Correct")) {
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.GREEN.getIndex());
                        style.setFont(headerFont);
                    }
                    if (type.equals("Incorrect")) {
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.RED.getIndex());
                        style.setFont(headerFont);
                    }
                }

                cell.setCellStyle(style);
                cell.setCellValue(val);
            }
        } catch (Throwable e) {
        }
        return sheet;
    }

    public static Collector<Sample, ?, Set<Sample>> getUniqueCollector(Function<Sample, String> compareFunction) {
        return Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(compareFunction)));
    }

    public static List<Sample> getUniqueSamplesByCmoIdLastWin(List<Sample> allSamples) {
        Collections.reverse(allSamples);
        List<Sample> samples = new LinkedList<>();

        Set<String> addedCmoIds = new HashSet<>();
        for (Sample sample : allSamples) {
            if(!addedCmoIds.contains(sample.getCmoSampleId()))
                samples.add(sample);
            addedCmoIds.add(sample.getCmoSampleId());
        }

        return samples;
    }

    public static AbstractResource getPropertiesLocation(String propertiesPath) {
        if (new File(propertiesPath).exists())
            return new FileSystemResource(propertiesPath);
        else
            return new ClassPathResource(propertiesPath);
    }

    public static String patientNormalization(String sample) {
        sample = sample.replace("-", "_");
        if (!sample.equals(Constants.NA_LOWER_CASE)) {
            sample = "p_" + sample;
        }
        return sample;
    }

    public static boolean isCmoSideProject(String projectManagerName) {
        if (StringUtils.isBlank(projectManagerName) || Constants.NO_PM.equals(projectManagerName)) {
            return true;
        }
        return false;
    }

    public static boolean isValidMSKemail(String email) {
        return Pattern.compile("^(.+)@(.*)mskcc\\.org$").matcher(email.toLowerCase()).matches();
    }
}
