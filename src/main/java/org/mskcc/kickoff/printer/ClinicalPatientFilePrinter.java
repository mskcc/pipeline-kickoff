package org.mskcc.kickoff.printer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public abstract class ClinicalPatientFilePrinter extends FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    public ClinicalPatientFilePrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    protected abstract void notifyObservers();

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return (request.getRequestType() != RequestType.RNASEQ && request.getRequestType() != RequestType.OTHER)
                && !(Utils.isExitLater() && !request.isInnovation() && request.getRequestType() != RequestType.OTHER
                && request.getRequestType() != RequestType.RNASEQ);
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return String.format("%s/%s%s", request.getOutputPath(), Utils
                .getFullProjectNameWithPrefix(request.getId()), getOutputFilenameEnding());
    }

    protected abstract Map<String, String> getManualHeader();

    protected abstract String getFileType();

    protected abstract String getOutputFilenameEnding();

    protected abstract Predicate<Sample> getSamplePredicate();

    protected abstract void writeExternalTumors(KickoffRequest kickoffRequest, StringBuilder output);

    @Override
    public void print(KickoffRequest kickoffRequest) {
        StringBuilder outputText = new StringBuilder();
        outputText.append(StringUtils.join(getManualHeader().keySet(), "\t"));
        outputText.append("\n");

        writeIgoTumors(kickoffRequest, outputText);
        writeExternalTumors(kickoffRequest, outputText);
        writeToFile(kickoffRequest, outputText);
    }

    private void writeIgoTumors(KickoffRequest kickoffRequest, StringBuilder outputText) {
        List<Sample> samples = kickoffRequest.getUniqueSamplesByCmoIdLastWin(getSamplePredicate());
        for (Sample sample : samples) {
            int size = getManualHeader().size();
            for (String fieldName : getManualHeader().values()) {
                String fieldValue = sample.get(fieldName);
                switch (fieldName) {
                    case Constants.CORRECTED_CMO_ID:
                        fieldValue = Utils.sampleNormalization(fieldValue);
                        break;
                    case Constants.INVESTIGATOR_SAMPLE_ID:
                        fieldValue = fieldValue.replace('-', '_');
                        break;
                    case Constants.CMO_PATIENT_ID:
                        fieldValue = Utils.patientNormalization(fieldValue);
                        break;
                    default:
                        break;
                }
                if (size -- > 1) {
                    outputText.append(fieldValue).append("\t");
                } else {
                    outputText.append(fieldValue).append("\n");
                }
            }
        }
    }

    private void writeToFile(KickoffRequest kickoffRequest, StringBuilder outputText) {
        if (outputText.length() > 0) {
            try {
                String text = Utils.filterToAscii(outputText.toString());

                String filename = getFilePath(kickoffRequest);
                File outputFile = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                pW.write(text);
                pW.close();

                notifyObservers();
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while creating file: %s", getOutputFilenameEnding()),
                        e);
            }
        }
    }

    protected String getIfAvailable(String field) {
        if (StringUtils.isEmpty(field))
            return Constants.NA;
        return field;
    }
}
