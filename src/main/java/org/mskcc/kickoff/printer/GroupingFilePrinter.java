package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.List;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

public class GroupingFilePrinter implements FilePrinter {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final DecimalFormat groupNumberFormat = new DecimalFormat("000");

    public void print(Request request) {
        String filename = String.format("%s/%s_sample_grouping.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()));

        StringBuilder outputText = new StringBuilder();

        for (Patient patient : request.getPatients().values()) {
            for (Sample sample : getUniqueSamples(patient)) {
                outputText.append(String.format("%s\tGroup_%s\n", sampleNormalization(sample.get(Constants.CORRECTED_CMO_ID)), groupNumberFormat.format(patient.getGroupNumber())));
            }
        }
        if (outputText.length() > 0) {
            try {
                //@TODO create Printer to deal with printing, closing, etc
                outputText = new StringBuilder(filterToAscii(outputText.toString()));
                File outputFile = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                pW.write(outputText.toString());
                pW.close();
                OutputFilesPrinter.filesCreated.add(outputFile);
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while creating grouping file: %s", filename), e);
            }
        }
    }

    private List<Sample> getUniqueSamples(Patient patient) {
        return Utils.getUniqueSamplesByCmoIdLastWin(new LinkedList<>(patient.getSamples()));
    }

    private boolean doPatientsExist(Request request) {
        if (request.getPatients().isEmpty()) {
            String message = "No patient sample map, therefore no grouping file created.";
            PM_LOGGER.log(PmLogPriority.WARNING, message);
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldPrint(Request request) {
        boolean patientsExist = doPatientsExist(request);
        return patientsExist
                && !(Utils.isExitLater() && !krista && !request.isInnovationProject() && !request.getRequestType().equals(Constants.OTHER) && !request.getRequestType().equals(Constants.RNASEQ))
                && (!request.getRequestType().equals(Constants.RNASEQ) && !request.getRequestType().equals(Constants.OTHER));
    }
}
