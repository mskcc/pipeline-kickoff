package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.kickoff.util.Utils.addRowToSheet;

class PairingXlsxPrinter implements FilePrinter  {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private String pairing_filename;
    private Map<String, String> pair_info;
    private Set<String> missingNormalsToBeAdded;

    public PairingXlsxPrinter(String pairing_filename, Map<String, String> pair_Info, Set<String> missingNormalsToBeAdded) {
        this.pairing_filename = pairing_filename;
        this.pair_info = pair_Info;
        this.missingNormalsToBeAdded = missingNormalsToBeAdded;
    }

    @Override
    public void print(Request request) {
        File pairingExcel = new File(pairing_filename.substring(0, pairing_filename.lastIndexOf('.')) + ".xlsx");

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet pairingInfo = wb.createSheet(Constants.PAIRING_INFO);
        int rowNum = 0;

        pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<>(Arrays.asList(Constants.TUMOR, Constants.MATCHED_NORMAL, Constants.SAMPLE_RENAME)), rowNum, Constants.EXCEL_ROW_TYPE_HEADER);
        rowNum++;

        for (String tum : pair_info.keySet()) {
            String norm = pair_info.get(tum);

            pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<>(Arrays.asList(tum, norm)), rowNum, null);
            rowNum++;
        }

        for (String unmatchedNorm : missingNormalsToBeAdded) {
            String tum = "na";
            pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<>(Arrays.asList(tum, unmatchedNorm)), rowNum, null);
            rowNum++;

        }

        try {
            //Now that the excel is done, print it to file
            FileOutputStream fileOUT = new FileOutputStream(pairingExcel);
            filesCreated.add(pairingExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while writing to file: %s", pairingExcel), e);
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return true;
    }
}
