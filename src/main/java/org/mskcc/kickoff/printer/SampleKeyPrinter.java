package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.mskcc.domain.LibType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Value;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;

public class SampleKeyPrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Value("${sampleKeyExamplesPath}")
    private String sampleKeyExamplesPath;

    @Value("${sampleKeyInstructionsPath}")
    private String sampleKeyInstructionsPath;

    @Override
    public void print(Request request) {
        // sample info
        String requestID = request.getId();
        File sampleKeyExcel = new File(request.getOutputPath() + "/" + Utils.getFullProjectNameWithPrefix(requestID)
                + "_sample_key.xlsx");

        //create the workbook
        XSSFWorkbook wb = new XSSFWorkbook();

        // Create a sheet in the workbook
        XSSFSheet sampleKey = wb.createSheet(Constants.SAMPLE_KEY);

        //set the row number
        int rowNum = 0;

        // Protect the whole sheet, unlock the cells that need unlocking
        sampleKey.protectSheet(Constants.RESERVED_TEST_PHRASE);

        CellStyle unlockedCellStyle = wb.createCellStyle();
        unlockedCellStyle.setLocked(false);

        String instructs = getInstructions();

        sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Collections.singletonList(instructs)), rowNum,
                Constants.Excel.INSTRUCTIONS);
        rowNum++;

        //header
        sampleKey = addRowToSheet(wb, sampleKey, Arrays.asList("FASTQFileID", "InvestigatorSampleID",
                "GroupName1", "GroupName2", "GroupName3", "GroupName4"), rowNum, Constants.EXCEL_ROW_TYPE_HEADER);
        rowNum++;

        List<Sample> samples = new ArrayList<>(request.getUniqueSamplesByCmoIdLastWin(s -> s.isValid()).stream()
                .sorted(Comparator.comparing(Sample::getIgoId))
                .collect(Collectors.toList()));

        for (Sample sample : samples) {
            Map<String, String> hash = sample.getProperties();
            String investSamp = hash.get(Constants.INVESTIGATOR_SAMPLE_ID);
            String seqIGOid = hash.get(Constants.SEQ_IGO_ID);
            if (seqIGOid == null) {
                seqIGOid = hash.get(Constants.IGO_ID);
            }

            String sampName1 = hash.get(Constants.CMO_SAMPLE_ID);
            String cmoSamp = sampName1 + "_IGO_" + seqIGOid;
            if (cmoSamp.startsWith("#")) {
                cmoSamp = sampName1 + "_IGO_" + seqIGOid;
            }
            sampleKey = addRowToSheet(wb, sampleKey, Arrays.asList(cmoSamp, investSamp), rowNum, null);

            unlockGroupNameCells(sampleKey, rowNum, unlockedCellStyle);
            rowNum++;
        }

        // EMPTY cell conditional formatting: color cell pink if there is nothing in it:
        SheetConditionalFormatting sheetCF = sampleKey.getSheetConditionalFormatting();
        ConditionalFormattingRule emptyRule = sheetCF.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"\"");
        PatternFormatting fill1 = emptyRule.createPatternFormatting();
        fill1.setFillBackgroundColor(IndexedColors.YELLOW.index);

        CellRangeAddress[] regions = {CellRangeAddress.valueOf("A1:C" + rowNum)};
        sheetCF.addConditionalFormatting(regions, emptyRule);

        addDataValidation(sampleKey, rowNum);

        // Lastly auto size the three columns I am using:
        for (int i = 0; i < 6; i++) {
            sampleKey.autoSizeColumn(0);
        }

        // Add extra sheet called Example that will have the example
        XSSFSheet exampleSheet = wb.createSheet(Constants.EXAMPLE);
        rowNum = 0;
        exampleSheet.protectSheet(Constants.RESERVED_TEST_PHRASE);

        // There are a couple different examples so I would like to
        // grab them from a tab-delim text file, and row by row add them to the excel.

        try {
            InputStream exStream = ClassLoader.getSystemResourceAsStream(sampleKeyExamplesPath);
            DataInputStream in = new DataInputStream(exStream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String exLine;

            while ((exLine = br.readLine()) != null) {
                String type = null;
                if (exLine.startsWith(Constants.CORRECT)) {
                    type = Constants.CORRECT;
                } else if (exLine.startsWith(Constants.INCORRECT)) {
                    type = Constants.INCORRECT;
                }

                String[] cellVals = exLine.split("\t");

                exampleSheet = addRowToSheet(wb, exampleSheet, new ArrayList<>(Arrays.asList(cellVals)), rowNum, type);
                rowNum++;
            }

        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("An Exception was thrown while creating sample key excel file: %s",
                    sampleKeyExcel), e);
        }
        // the example sheet has a header for each example, so I can't auto size that column.
        exampleSheet.setColumnWidth(0, (int) (exampleSheet.getColumnWidth(0) * 2.2));
        exampleSheet.autoSizeColumn(1);
        exampleSheet.autoSizeColumn(2);

        try {
            FileOutputStream fileOUT = new FileOutputStream(sampleKeyExcel);
            filesCreated.add(sampleKeyExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while writing to file: %s", sampleKeyExcel), e);
        }
    }

    private String getInstructions() {
        try {
            return new String(Files.readAllBytes(Paths.get(sampleKeyInstructionsPath)));
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Unable to read Sample Key instructions from %s. Using default one.",
                    sampleKeyInstructionsPath));
            return getDafaultInstructions();
        }
    }

    private String getDafaultInstructions() {
        return "Instructions:\n" +
                "   -Fill in the GroupName column (column C) for each sample. Columns D-F can be used if your " +
                "experiment" +
                " has multiple pairings, see below.\n" +
                "                -Please do not leave any blank fields in this column. If there are samples that " +
                "should not be included, identify these with the GroupName “excluded”.\n" +
                "                -Please be consistent when assigning group names.\n" +
                "                -GroupNames are case sensitive. For example, “Normal” and “normal” will be " +
                "identified as two different group names.\n" +
                "                -GroupNames should start with a letter and only use characters, A-Z and 0-9. Please " +
                "do not use any special characters (i.e. ‘&’, ‘#’, ‘$’ etc) or spaces when assigning a GroupName.\n" +
                "   -If your experiment does not have multiple pairings, please only edit column C.\n" +
                "   -If your experiment has multiple pairings, please indicate additional GroupNames in columns D, E," +
                " " +
                "and F.\n" +
                "                -Do not change any of the information in columns A or B.\n" +
                "                -Do not rename the sample IDs (InvestigatorSampleID or FASTQFileID). If you have a " +
                "question about the sample names, please email bic-request@cbio.mskcc.org.\n" +
                "                -Do not make any other changes to this file.\n";
    }

    private void addDataValidation(XSSFSheet sampleKey, int rowNum) {
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sampleKey);

        CellRangeAddressList rangeList = new CellRangeAddressList();
        rangeList.addCellRangeAddress(CellRangeAddress.valueOf("A1:C" + rowNum));

        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper
                .createTextLengthConstraint(ComparisonOperator.GE, "15", null);
        XSSFDataValidation dv1 = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, rangeList);

        dv1.setShowErrorBox(true);
        sampleKey.addValidationData(dv1);
    }

    private void unlockGroupNameCells(XSSFSheet sampleKey, int rowNum, CellStyle unlockedCellStyle) {
        Row thisRow = sampleKey.getRow(rowNum);

        for (int i = 2; i < 6; i++) {
            thisRow.createCell(i).setCellStyle(unlockedCellStyle);
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return !(Utils.isExitLater() && !krista && !request.isInnovationProject() && !request.getRequestType().equals
                (Constants.OTHER) && !request.getRequestType().equals(Constants.RNASEQ))
                && (request.getRequestType().equals(Constants.RNASEQ) && !request.getLibTypes().contains(LibType
                .TRU_SEQ_FUSION_DISCOVERY) && request.getAllValidSamples().size() > 0);
    }

    private XSSFSheet addRowToSheet(XSSFWorkbook wb, XSSFSheet sheet, List<String> columnNames, int rowNum, String
            type) {
        try {
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum = 0;
            for (String val : columnNames) {
                if (val == null || val.isEmpty()) {
                    val = Constants.Excel.EMPTY;
                }
                XSSFCell cell = row.createCell(cellNum++);
                XSSFCellStyle style = wb.createCellStyle();
                XSSFFont headerFont = wb.createFont();

                if (type != null) {
                    if (type.equals(Constants.EXCEL_ROW_TYPE_HEADER)) {
                        headerFont.setBold(true);
                        style.setFont(headerFont);
                    }
                    if (type.equals(Constants.Excel.INSTRUCTIONS)) {
                        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 20));
                        style.setWrapText(true);
                        row.setRowStyle(style);

                        int numberOfLines = val.split("\n").length + 2;
                        row.setHeight((short) (row.getHeight() * numberOfLines));
                    }
                    if (type.equals(Constants.CORRECT)) {
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.GREEN.getIndex());
                        style.setFont(headerFont);
                    }
                    if (type.equals(Constants.INCORRECT)) {
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.RED.getIndex());
                        style.setFont(headerFont);
                    }
                }

                cell.setCellStyle(style);
                cell.setCellValue(val);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while adding row to xlsx sheet"), e);
        }
        return sheet;
    }
}
