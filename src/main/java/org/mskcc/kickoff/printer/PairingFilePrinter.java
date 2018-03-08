package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.PairingsResolver;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mskcc.kickoff.config.Arguments.shiny;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

@Component
public class PairingFilePrinter extends FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final PairingsResolver pairingsResolver;

    @Autowired
    public PairingFilePrinter(PairingsResolver pairingsResolver, ObserverManager observerManager) {
        super(observerManager);
        this.pairingsResolver = pairingsResolver;
    }

    @Override
    public void print(KickoffRequest request) {
        String filename = getFilePath(request);
        DEV_LOGGER.info(String.format("Starting to create file: %s", filename));

        Map<String, String> pairingInfo = getPairingInfo(request);

        Set<String> normalCMOids = new HashSet<>();
        for (Sample sample : request.getAllValidSamples().values()) {
            if (sample.get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL)) {
                normalCMOids.add(sample.get(Constants.CORRECTED_CMO_ID));
            }
        }

        Set<String> missingNormalsToBeAdded = new HashSet<>();
        if (request.getRequestType() == RequestType.EXOME) {
            HashSet<String> normalsPairedWithThings = new HashSet<>(pairingInfo.values());
            missingNormalsToBeAdded = new HashSet<>(normalCMOids);
            missingNormalsToBeAdded.removeAll(normalsPairedWithThings);
        }
        try {
            if (pairingInfo != null && pairingInfo.size() > 0) {
                File pairing_file = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                for (String tum : pairingInfo.keySet()) {
                    String norm = pairingInfo.get(tum);
                    pW.write(sampleNormalization(norm) + "\t" + sampleNormalization(tum) + "\n");

                    notifyIfTumorUnmatched(request, tum, norm);
                }
                for (String unmatchedNorm : missingNormalsToBeAdded) {
                    String tum = "na";
                    pW.write(sampleNormalization(unmatchedNorm) + "\t" + sampleNormalization(tum) + "\n");
                }

                pW.close();

                observerManager.notifyObserversOfFileCreated(request, ManifestFile.PAIRING);

                if (shiny) {
                    printPairingExcel(request, filename, pairingInfo, missingNormalsToBeAdded);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown: ", e);
        }
    }

    private void notifyIfTumorUnmatched(KickoffRequest request, String tum, String norm) {
        if (Constants.NA_LOWER_CASE.equals(norm)) {
            String message = String.format("No normal sample for tumor %s", tum);
            observerManager.notifyObserversOfError(request, ManifestFile.PAIRING, message, GenerationError.INSTANCE);
        }
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return String.format("%s/%s_sample_pairing.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix
                (request.getId()));
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return !(Utils.isExitLater() && !request.isInnovation() && !request.getRequestType().equals(RequestType
                .OTHER) && !request.getRequestType().equals(RequestType.RNASEQ))
                && (!request.getRequestType().equals(RequestType.OTHER) && !request.getRequestType().equals
                (RequestType.RNASEQ));
    }

    private Map<String, String> getPairingInfo(KickoffRequest request) {
        return pairingsResolver.resolve(request);
    }

    private void printPairingExcel(KickoffRequest request, String pairing_filename, Map<String, String> pair_Info,
                                   Set<String> missingNormalsToBeAdded) {
        new PairingXlsxPrinter(pairing_filename, pair_Info, missingNormalsToBeAdded, observerManager).print(request);
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
