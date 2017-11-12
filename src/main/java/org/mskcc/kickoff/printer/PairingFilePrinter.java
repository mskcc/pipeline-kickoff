package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.generator.PairingsResolver;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mskcc.kickoff.config.Arguments.shiny;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

public class PairingFilePrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final PairingsResolver pairingsResolver;

    public PairingFilePrinter(PairingsResolver pairingsResolver) {
        this.pairingsResolver = pairingsResolver;
    }

    @Override
    public void print(KickoffRequest request) {
        String filename = String.format("%s/%s_sample_pairing.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()));
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
                }
                for (String unmatchedNorm : missingNormalsToBeAdded) {
                    String tum = "na";
                    pW.write(sampleNormalization(unmatchedNorm) + "\t" + sampleNormalization(tum) + "\n");
                }

                pW.close();

                if (shiny) {
                    printPairingExcel(request, filename, pairingInfo, missingNormalsToBeAdded);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown: ", e);
        }
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return !(Utils.isExitLater() && !request.isInnovation() && !request.getRequestType().equals(RequestType.OTHER) && !request.getRequestType().equals(RequestType.RNASEQ))
                && (!request.getRequestType().equals(RequestType.OTHER) && !request.getRequestType().equals(RequestType.RNASEQ));
    }

    private Map<String, String> getPairingInfo(KickoffRequest request) {
        return pairingsResolver.resolve(request);
    }

    private void printPairingExcel(KickoffRequest request, String pairing_filename, Map<String, String> pair_Info, Set<String> missingNormalsToBeAdded) {
        new PairingXlsxPrinter(pairing_filename, pair_Info, missingNormalsToBeAdded).print(request);
    }
}
