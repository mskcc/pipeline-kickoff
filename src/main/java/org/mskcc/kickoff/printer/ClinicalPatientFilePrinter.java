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

import static org.mskcc.kickoff.util.Utils.patientNormalization;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

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
            for (String fieldName : getManualHeader().values()) {
                String value = normalizeIfNeeded(fieldName, sample.get(fieldName));
                outputText.append(value);
                outputText.append("\t");
            }
            outputText.append("\n");
        }
    }

    private void writeExternalTumors(KickoffRequest kickoffRequest, StringBuilder outputText) {
        for (KickoffExternalSample externalSample : kickoffRequest.getTumorExternalSamples()) {
            outputText.append(sampleNormalization(externalSample.getCmoId()));
            outputText.append("\t");

            outputText.append(patientNormalization(externalSample.getPatientCmoId()));
            outputText.append("\t");

            outputText.append(externalSample.getExternalId());
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSpecimenType()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getBaitVersion()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getOncotreeCode()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSampleClass()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getPreservationType()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSex()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getTissueSite()));
            outputText.append("\n");
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

    private String getIfAvailable(String field) {
        if (StringUtils.isEmpty(field))
            return Constants.NA;
        return field;
    }

    private String normalizeIfNeeded(String fieldName, String value) {
        if (isSampleField(fieldName))
            return sampleNormalization(value);
        if (isPatientField(fieldName))
            return Utils.patientNormalization(value);
        return value;
    }

    private boolean isSampleField(String fieldName) {
        return Constants.CORRECTED_CMO_ID.equals(fieldName);
    }

    private boolean isPatientField(String fieldName) {
        return Constants.CMO_PATIENT_ID.equals(fieldName);
    }
}
