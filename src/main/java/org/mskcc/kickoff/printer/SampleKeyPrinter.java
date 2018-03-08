package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.mskcc.domain.LibType;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class SampleKeyPrinter extends FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Value("${sampleKeyExamplesPath}")
    private String sampleKeyExamplesPath;

    @Autowired
    public SampleKeyPrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    @Override
    public void print(KickoffRequest request) {
        DEV_LOGGER.info(String.format("Starting to create file: %s", getFilePath(request)));

        File sampleKeyExcel = new File(getFilePath(request));

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

        // First put the directions.
        String instructs = "Instructions:\n    - Fill in the GroupName column for each sample.\n        - Please do not leave any blank fields in this column.\n        - Please be consistent when assigning group names." +
                "\n        - GroupNames are case sensitive. For example, “Normal” and “normal” will be identified as two different group names.\n        - GroupNames should start with a letter  and only use characters, A-Z and 0-9. " +
                "Please do not use any special characters (i.e. '&', '#', '$' etc) or spaces when assigning a GroupName.\n    - Please only edit column C. Do not make any other changes to this file.\n        " +
                "- Do not change any of the information in columns A or B.\n        - Do not rename the samples IDs (InvestigatorSampleID or FASTQFileID). If you have a question about the sample names, please email " +
                "bic-request@cbio.mskcc.org.\n        - Do not reorder or rename the columns.\n        - Do not use the GroupName column to communicate any other information (such as instructions, comments, etc)";

        sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Collections.singletonList(instructs)), rowNum, Constants.Excel.INSTRUCTIONS);
        rowNum++;

        //header
        sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Arrays.asList("FASTQFileID", "InvestigatorSampleID", "GroupName")), rowNum, "header");
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
            sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Arrays.asList(cmoSamp, investSamp)), rowNum, null);

            // Unlock this rows third cell
            Row thisRow = sampleKey.getRow(rowNum);
            Cell cell3 = thisRow.createCell(2);  // zero based
            cell3.setCellStyle(unlockedCellStyle);

            rowNum++;
        }

        // EMPTY cell conditional formatting: color cell pink if there is nothing in it:
        SheetConditionalFormatting sheetCF = sampleKey.getSheetConditionalFormatting();
        ConditionalFormattingRule emptyRule = sheetCF.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"\"");
        PatternFormatting fill1 = emptyRule.createPatternFormatting();
        fill1.setFillBackgroundColor(IndexedColors.YELLOW.index);

        CellRangeAddress[] regions = {CellRangeAddress.valueOf("A1:C" + rowNum)};
        sheetCF.addConditionalFormatting(regions, emptyRule);

        // DATA VALIDATION
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sampleKey);
        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper.createTextLengthConstraint(ComparisonOperator.GE, "15", null);
        CellRangeAddressList rangeList = new CellRangeAddressList();
        rangeList.addCellRangeAddress(CellRangeAddress.valueOf("A1:C" + rowNum));
        XSSFDataValidation dv1 = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, rangeList);
        dv1.setShowErrorBox(true);
        sampleKey.addValidationData(dv1);

        // Lastly auto size the three columns I am using:
        sampleKey.autoSizeColumn(0);
        sampleKey.autoSizeColumn(1);
        sampleKey.autoSizeColumn(2);


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
            DEV_LOGGER.warn(String.format("An Exception was thrown while creating sample key excel file: %s", sampleKeyExcel), e);
        }
        // the example sheet has a header for each example, so I can't auto size that column.
        exampleSheet.setColumnWidth(0, (int) (exampleSheet.getColumnWidth(0) * 2.2));
        exampleSheet.autoSizeColumn(1);
        exampleSheet.autoSizeColumn(2);

        try {
            FileOutputStream fileOUT = new FileOutputStream(sampleKeyExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while writing to file: %s", sampleKeyExcel), e);
        }
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return request.getOutputPath() + "/" + Utils.getFullProjectNameWithPrefix(request.getId()) + "_sample_key.xlsx";
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return request.getRequestType() == RequestType.RNASEQ && !request.getLibTypes().contains(LibType
                .TRU_SEQ_FUSION_DISCOVERY) && request.getAllValidSamples().size() > 1;
    }

    private XSSFSheet addRowToSheet(XSSFWorkbook wb, XSSFSheet sheet, ArrayList<String> list, int rowNum, String type) {
        try {
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum = 0;
            for (String val : list) {
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
