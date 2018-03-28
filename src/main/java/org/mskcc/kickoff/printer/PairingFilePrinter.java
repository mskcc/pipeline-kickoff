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
import java.util.stream.Collectors;

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
        populatePairingSampleIds(request, pairingInfo);

        Set<String> normalCMOids = request.getAllValidSamples(s -> !s.isTumor()).values().stream()
                .map(Sample::getCorrectedCmoSampleId)
                .collect(Collectors.toSet());

        try {
            if (pairingInfo.size() > 0) {
                File pairing_file = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                for (String tumorCorrectedCmoId : pairingInfo.keySet()) {
                    String normalCorrectedCmoId = pairingInfo.get(tumorCorrectedCmoId);
                    pW.write(sampleNormalization(normalCorrectedCmoId) + "\t" + sampleNormalization
                            (tumorCorrectedCmoId) + "\n");
                    notifyIfTumorUnmatched(tumorCorrectedCmoId, normalCorrectedCmoId);
                }
                pairUnmatchedNormals(request, pairingInfo, normalCMOids, pW);

                pW.close();
                observerManager.notifyObserversOfFileCreated(ManifestFile.PAIRING);
            }
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Error while generating pairing file: %s", e.getMessage()), e);
        }
    }

    private void populatePairingSampleIds(KickoffRequest request, Map<String, String> pairingInfo) {
        request.addPairingSampleIds(pairingInfo.keySet().stream()
                .filter(pi -> !Constants.NA_LOWER_CASE.equals(pi))
                .collect(Collectors.toSet()));

        request.addPairingSampleIds(pairingInfo.values().stream()
                .filter(pi -> !Constants.NA_LOWER_CASE.equals(pi))
                .collect(Collectors.toSet()));
    }

    private void notifyNormalUnmatched(String unmatchedNorm) {
        GenerationError generationError = new GenerationError(String.format("No tumor sample for normal %s",
                unmatchedNorm), ErrorCode.UNMATCHED_NORMAL);
        observerManager.notifyObserversOfError(ManifestFile.PAIRING, generationError);
    }

    private void notifyIfTumorUnmatched(String tum, String norm) {
        if (Constants.NA_LOWER_CASE.equals(norm)) {
            GenerationError generationError = new GenerationError(String.format("No normal sample for tumor %s", tum),
                    ErrorCode.UNMATCHED_TUMOR);
            observerManager.notifyObserversOfError(ManifestFile.PAIRING, generationError);
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
                if (!unmatchedNorm.matches("%POOLEDNORMAL%"))
                    request.addPairingSampleId(unmatchedNorm);
                notifyNormalUnmatched(unmatchedNorm);
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
        new PairingXlsxPrinter(pairing_filename, pair_Info, missingNormalsToBeAdded, observerManager).print(request);
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
