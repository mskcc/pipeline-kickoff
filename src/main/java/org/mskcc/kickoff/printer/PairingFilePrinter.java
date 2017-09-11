package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.generator.PairingsResolver;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.config.Arguments.shiny;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.kickoff.util.Utils.sampleNormalization;

public class PairingFilePrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private Map<String, String> pairingInfo;
    private PairingsResolver pairingsResolver;

    public PairingFilePrinter(PairingsResolver pairingsResolver) {
        this.pairingsResolver = pairingsResolver;
    }

    @Override
    public void print(Request request) {
        pairingInfo = getPairingInfo(request);
        String filename = String.format("%s/%s_sample_pairing.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()));

        Set<String> normalCMOids = new HashSet<>();
        // Generate normal CMO IDs
        for (Sample sample : request.getAllValidSamples().values()) {
            if (sample.get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL)) {
                normalCMOids.add(sample.get(Constants.CORRECTED_CMO_ID));
            }
        }

        Set<String> missingNormalsToBeAdded = new HashSet<>();
        if (Objects.equals(request.getRequestType(), Constants.EXOME)) {
            // Make a list of all the unmatched normals, so they can be added to the end of the pairing
            HashSet<String> normalsPairedWithThings = new HashSet<>(pairingInfo.values());
            missingNormalsToBeAdded = new HashSet<>(normalCMOids);
            missingNormalsToBeAdded.removeAll(normalsPairedWithThings);
        }
        try {
            //Done going through all igo ids, now print
            if (pairingInfo != null && pairingInfo.size() > 0) {
                File pairing_file = new File(filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                filesCreated.add(pairing_file);
                for (String tum : pairingInfo.keySet()) {
                    String norm = pairingInfo.get(tum);
                    pW.write(sampleNormalization(norm) + "\t" + sampleNormalization(tum) + "\n");
                }
                for (String unmatchedNorm : missingNormalsToBeAdded) {
                    String tum = "na";
                    pW.write(sampleNormalization(unmatchedNorm) + "\t" + sampleNormalization(tum) + "\n");
                }

                pW.close();

                if (shiny || krista) {
                    printPairingExcel(request, filename, pairingInfo, missingNormalsToBeAdded);
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown: ", e);
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return !(Utils.isExitLater() && !krista && !request.isInnovationProject() && !request.getRequestType().equals(Constants.OTHER) && !request.getRequestType().equals(Constants.RNASEQ))
                && (!request.getRequestType().equals(Constants.RNASEQ) && !request.getRequestType().equals(Constants.OTHER));
    }

    private Map<String, String> getPairingInfo(Request request) {
        return pairingsResolver.resolve(request);
    }

    private void printPairingExcel(Request request, String pairing_filename, Map<String, String> pair_Info, Set<String> missingNormalsToBeAdded) {
        new PairingXlsxPrinter(pairing_filename, pair_Info, missingNormalsToBeAdded).print(request);
    }
}
