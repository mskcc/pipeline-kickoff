package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.PairingsResolver;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mskcc.kickoff.util.Utils.sampleNormalization;

@Component
public class PairingFilePrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final PairingsResolver pairingsResolver;
    private final ObserverManager observerManager = new ObserverManager();

    @Autowired
    public PairingFilePrinter(PairingsResolver pairingsResolver) {
        this.pairingsResolver = pairingsResolver;
    }

    @Override
    public void print(KickoffRequest request) {
        String filename = getFilePath(request);
        DEV_LOGGER.info(String.format("Starting to create file: %s", filename));

        Map<String, String> pairingInfo = getPairingInfo(request);

        Set<String> normalCMOids = request.getAllValidSamples(s -> !s.isTumor()).values().stream()
                .map(s -> s.getCorrectedCmoSampleId())
                .collect(Collectors.toSet());

        try {
            if (pairingInfo != null && pairingInfo.size() > 0) {
                File pairing_file = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                for (String tumorCorrectedCmoId : pairingInfo.keySet()) {
                    String normalCorrectedCmoId = pairingInfo.get(tumorCorrectedCmoId);
                    pW.write(sampleNormalization(normalCorrectedCmoId) + "\t" + sampleNormalization
                            (tumorCorrectedCmoId) + "\n");
                }
                pairUnmatchedNormals(request, pairingInfo, normalCMOids, pW);

                pW.close();

                observerManager.notifyObserversOfFileCreated(request, ManifestFile.PAIRING);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown: ", e);
        }
    }

    private void pairUnmatchedNormals(KickoffRequest request, Map<String, String> pairingInfo, Set<String>
            normalCMOids, PrintWriter pW) {
        if (request.getRequestType() == RequestType.EXOME) {
            Set<String> pairedNormals = new HashSet<>(pairingInfo.values());
            Set<String> missingNormalsToBeAdded = new HashSet<>(normalCMOids);
            missingNormalsToBeAdded.removeAll(pairedNormals);
            for (String unmatchedNorm : missingNormalsToBeAdded) {
                pW.write(String.format("%s\t%s\n", sampleNormalization(unmatchedNorm), Constants.NA_LOWER_CASE));
            }
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
        new PairingXlsxPrinter(pairing_filename, pair_Info, missingNormalsToBeAdded).print(request);
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
