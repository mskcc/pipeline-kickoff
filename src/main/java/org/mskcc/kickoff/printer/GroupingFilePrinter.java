package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.Patient;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

@Component
public class GroupingFilePrinter extends FilePrinter {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final DecimalFormat groupNumberFormat = new DecimalFormat("000");

    @Autowired
    public GroupingFilePrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    public void print(KickoffRequest kickoffRequest) {
        String filename = getFilePath(kickoffRequest);
        DEV_LOGGER.info(String.format("Starting to create file: %s", filename));

        StringBuilder outputText = new StringBuilder();

        for (Patient patient : getPatientsListSortedByGroup(kickoffRequest)) {
            for (Sample sample : getUniqueSamples(patient)) {
                if (kickoffRequest.getPairingSampleIds().contains(sample.getCorrectedCmoSampleId()))
                    outputText.append(String.format("%s\tGroup_%s\n", sampleNormalization(sample.get(Constants
                            .CORRECTED_CMO_ID)), groupNumberFormat.format(patient.getGroupNumber())));
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
                observerManager.notifyObserversOfFileCreated(ManifestFile.GROUPING);
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while creating grouping file: %s", filename), e);
            }
        }
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return String.format("%s/%s_sample_grouping.txt", request.getOutputPath(), Utils
                .getFullProjectNameWithPrefix(request.getId()));
    }

    private List<Patient> getPatientsListSortedByGroup(KickoffRequest kickoffRequest) {
        return kickoffRequest.getPatients().values().stream()
                .sorted(Comparator.comparingInt(Patient::getGroupNumber))
                .collect(Collectors.toList());
    }

    private List<Sample> getUniqueSamples(Patient patient) {
        return Utils.getUniqueSamplesByCmoIdLastWin(new LinkedList<>(patient.getSamples()));
    }

    private boolean doPatientsExist(KickoffRequest kickoffRequest) {
        if (kickoffRequest.getPatients().isEmpty()) {
            String message = "No patient sample map, therefore no grouping file created.";
            PM_LOGGER.log(PmLogPriority.WARNING, message);
            return false;
        }
        return true;
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        boolean patientsExist = doPatientsExist(request);
        return patientsExist
                && !(Utils.isExitLater() && !request.isInnovation() && request.getRequestType() != RequestType.OTHER
                && request.getRequestType() != RequestType.RNASEQ)
                && (request.getRequestType() != RequestType.RNASEQ && request.getRequestType() != RequestType.OTHER);
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
