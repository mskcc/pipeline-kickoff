package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.ConditionalFormattingRule;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.PatternFormatting;
import org.apache.poi.ss.usermodel.SheetConditionalFormatting;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.util.Constants.MAX_HEADER_SIZE;

public class ManifestFilePrinter implements FilePrinter  {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final List<String> hashMapHeader = Arrays.asList(Constants.MANIFEST_SAMPLE_ID, Constants.CMO_PATIENT_ID, Constants.INVESTIGATOR_SAMPLE_ID, Constants.INVESTIGATOR_PATIENT_ID, Constants.ONCOTREE_CODE, Constants.SAMPLE_CLASS, Constants.TISSUE_SITE, Constants.SAMPLE_TYPE, Constants.SPECIMEN_PRESERVATION_TYPE, Constants.Excel.SPECIMEN_COLLECTION_YEAR, "SEX", "BARCODE_ID", "BARCODE_INDEX", "LIBRARY_INPUT", "LIBRARY_YIELD", "CAPTURE_INPUT", "CAPTURE_NAME", "CAPTURE_CONCENTRATION", Constants.CAPTURE_BAIT_SET, Constants.SPIKE_IN_GENES, Constants.STATUS, Constants.INCLUDE_RUN_ID, Constants.
            EXCLUDE_RUN_ID);
    private static final List<String> exceptionList = Arrays.asList(Constants.TISSUE_SITE, Constants.Excel.SPECIMEN_COLLECTION_YEAR, Constants.SPIKE_IN_GENES);
    private static final List<String> silentList = Arrays.asList(Constants.STATUS, Constants.EXCLUDE_RUN_ID);
    private static final String manualMappingHashMap = "LIBRARY_INPUT:LIBRARY_INPUT[ng],LIBRARY_YIELD:LIBRARY_YIELD[ng],CAPTURE_INPUT:CAPTURE_INPUT[ng],CAPTURE_CONCENTRATION:CAPTURE_CONCENTRATION[nM],MANIFEST_SAMPLE_ID:CMO_SAMPLE_ID";

    @Override
    public void print(Request request) {
        String filename = String.format("%s/%s_sample_manifest.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()));

        try {
            char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
            String manifestFileName = filename.substring(0, filename.lastIndexOf('.')) + ".xlsx";
            XSSFWorkbook wb = new XSSFWorkbook();
            XSSFSheet sampleInfoSheet = wb.createSheet(Constants.Manifest.SAMPLE_INFO);

            int rowNum = 0;
            XSSFSheet sampleRenames = wb.createSheet(Constants.Manifest.SAMPLE_RENAMES);
            sampleRenames = Utils.addRowToSheet(wb, sampleRenames, new ArrayList<>(Arrays.asList(Constants.Manifest.OLD_NAME, Constants.Manifest.NEW_NAME)), rowNum, Constants.EXCEL_ROW_TYPE_HEADER);

            if (request.getNewMappingScheme() == 1) {
                for (Sample sample : request.getUniqueSamplesByCmoIdLastWin()) {
                    rowNum++;
                    ArrayList<String> replaceNames = new ArrayList<>(Arrays.asList(sample.get(Constants.MANIFEST_SAMPLE_ID), sample.get(Constants.CORRECTED_CMO_ID)));
                    sampleRenames = Utils.addRowToSheet(wb, sampleRenames, replaceNames, rowNum, null);
                }
            }

            rowNum = 0;

            String[] header0 = hashMapHeader.toArray(new String[hashMapHeader.size()]);
            ArrayList<String> header = new ArrayList<>(Arrays.asList(header0));

            // Fixing header names
            ArrayList<String> replace = new ArrayList<>(Arrays.asList(manualMappingHashMap.split(",")));
            for (String fields : replace) {
                String[] parts = fields.split(":");
                int indexHeader = header.indexOf(parts[0]);
                header.set(indexHeader, parts[1]);
            }

            try {
                sampleInfoSheet = getHeader(wb, rowNum, header, sampleInfoSheet);
                rowNum++;

                // output each line, in order!
                for (Sample sample : request.getUniqueSamplesByCmoIdLastWin()) {
                    sampleInfoSheet = addRowToSheet(sampleInfoSheet, sample.getProperties(), rowNum, request);
                    rowNum++;
                }

                //Conditional Format
                //The formula says : IF there is a # in cell, IF the # is in the first position, return true
                //otherwise return false.
                SheetConditionalFormatting sheetCF = sampleInfoSheet.getSheetConditionalFormatting();
                ConditionalFormattingRule hashTagRule = sheetCF.createConditionalFormattingRule("IF(ISNUMBER(FIND(\"#\",A1)),IF(FIND(\"#\",A1)=1,1,0),0)");
                PatternFormatting fill1 = hashTagRule.createPatternFormatting();
                fill1.setFillBackgroundColor(IndexedColors.PINK.index);

                //Make a range that is at least the dimensions of the Hmap
                // do the header.size() divided by 26. That is how many blah. Then get the remainder, and that is the second letter.
                String lastSpot;
                if (header.size() > MAX_HEADER_SIZE) {
                    int firstLetter = header.size() / MAX_HEADER_SIZE;
                    int remainder = header.size() - (MAX_HEADER_SIZE * firstLetter);
                    lastSpot = alphabet[firstLetter - 1] + alphabet[remainder - 1] + String.valueOf(request.getUniqueSamplesByCmoIdLastWin().size() + 1);
                } else {
                    lastSpot = alphabet[header.size() - 1] + String.valueOf(request.getUniqueSamplesByCmoIdLastWin().size() + 1);
                }
                CellRangeAddress[] regions = {CellRangeAddress.valueOf("A1:" + lastSpot)};

                sheetCF.addConditionalFormatting(regions, hashTagRule);

                //Now that the excel is done, print it to file
                FileOutputStream fileOUT = new FileOutputStream(manifestFileName);
                wb.write(fileOUT);
                filesCreated.add(new File(manifestFileName));
                fileOUT.close();
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while writing to file: %s", manifestFileName), e);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating mapping file", e));
        }
    }

    private XSSFSheet getHeader(XSSFWorkbook wb, int rowNum, ArrayList<String> header, XSSFSheet sampleInfoSheet) {
        return Utils.addRowToSheet(wb, sampleInfoSheet, header, rowNum, Constants.EXCEL_ROW_TYPE_HEADER);
    }

    public XSSFSheet addRowToSheet(XSSFSheet sheet, Map<String, String> map, int rowNum, Request request) {
        try {
            ArrayList<String> header = new ArrayList<>(hashMapHeader);
            // If this is the old mapping scheme, don't make the CMO Sample ID include IGO ID.
            if (request.getNewMappingScheme() == 0) {
                int indexHeader = header.indexOf(Constants.MANIFEST_SAMPLE_ID);
                header.set(indexHeader, Constants.CORRECTED_CMO_ID);
            }
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum = 0;
            for (String key : header) {
                if (map.get(key) == null || map.get(key).isEmpty()) {
                    if (exceptionList.contains(key)) {
                        map.put(key, Constants.NA_LOWER_CASE);
                        if (Objects.equals(key, Constants.Excel.SPECIMEN_COLLECTION_YEAR)) {
                            map.put(key, "000");
                        }
                    } else if (silentList.contains(key)) {
                        map.put(key, "");
                    } else {
                        map.put(key, Constants.Excel.EMPTY);
                    }
                }

                row.createCell(cellNum++).setCellValue(map.get(key));
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while adding row to xlsx sheet"), e);
        }
        return sheet;
    }

    @Override
    public boolean shouldPrint(Request request) {
        return (!request.getRequestType().equals(Constants.RNASEQ) && !request.getRequestType().equals(Constants.OTHER))
                && !(Utils.isExitLater() && !krista && !request.isInnovationProject() && !request.getRequestType().equals(Constants.OTHER) && !request.getRequestType().equals(Constants.RNASEQ));
    }
}
