package org.mskcc.kickoff.printer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.Sample;
import org.mskcc.kickoff.domain.*;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.Normalizer;
import java.util.*;
import java.util.function.Predicate;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

public abstract class ClinicalPatientFilePrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public void print(Request request) {
        LinkedHashMap<String, String> fieldMapping = new LinkedHashMap<>();
        ArrayList<String> fieldList = new ArrayList<>(Arrays.asList(getManualHeader().split(",")));
        for (String fields : fieldList) {
            String[] parts = fields.split(":");
            fieldMapping.put(parts[0], parts[1]);
        }

        String outputText = StringUtils.join(fieldMapping.keySet(), "\t");
        outputText += "\n";
        outputText = filterToAscii(outputText);

        List<Sample> samples = request.getUniqueSamplesByCmoIdLastWin(getSamplePredicate());
        for (Sample sample : samples) {
            ArrayList<String> line = new ArrayList<>();
            for (String key : fieldMapping.values()) {
                String val = sample.get(key);

                if (key.equals(Constants.CMO_SAMPLE_ID) || key.equals(Constants.INVESTIGATOR_SAMPLE_ID)) {
                    val = sampleNormalization(val);
                }
                if (key.equals(Constants.CMO_PATIENT_ID) || key.equals(Constants.INVESTIGATOR_PATIENT_ID)) {
                    val = patientNormalization(val);
                }
                if (key.equals(Constants.SAMPLE_CLASS) && getFileType().equals(Constants.PATIENT)) {
                    if (!val.contains(Constants.NORMAL)) {
                        val = Constants.TUMOR;
                    }
                }
                line.add(val);
            }
            outputText += StringUtils.join(line, "\t");
            outputText += "\n";
        }

        if (outputText.length() > 0) {
            try {
                outputText = filterToAscii(outputText);

                String filename = String.format("%s/%s%s", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()), getOutputFilenameEnding());
                File outputFile = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                pW.write(outputText);
                pW.close();
                OutputFilesPrinter.filesCreated.add(outputFile);
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while creating file: %s", getOutputFilenameEnding()), e);
            }
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return (!request.getRequestType().equals(Constants.RNASEQ) && !request.getRequestType().equals(Constants.OTHER))
                && !(Utils.isExitLater() && !krista && !request.isInnovationProject() && !request.getRequestType().equals(Constants.OTHER) && !request.getRequestType().equals(Constants.RNASEQ));
    }

    protected abstract String getManualHeader();

    protected abstract String getFileType();

    protected abstract String getOutputFilenameEnding();

    protected abstract Predicate<Sample> getSamplePredicate();

    private String filterToAscii(String highUnicode) {
        String lettersAdded = highUnicode.replaceAll("ß", "ss").replaceAll("æ", "ae").replaceAll("Æ", "Ae");
        return Normalizer.normalize(lettersAdded, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }

    private String patientNormalization(String sample) {
        sample = sample.replace("-", "_");
        if (!sample.equals(Constants.NA_LOWER_CASE)) {
            sample = "p_" + sample;
        }
        return sample;
    }
}
