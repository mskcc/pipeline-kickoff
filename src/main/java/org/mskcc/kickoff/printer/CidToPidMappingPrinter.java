package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;

public class CidToPidMappingPrinter implements FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private String mappingFileName = "sample_c_to_p_mapping.txt";

    @Override
    public void print(Request request) {
        try {
            String mappingFileContents = "";

            for (Sample sample : request.getAllValidSamples().values()) {
                String correctedCmoId = sample.get(Constants.CORRECTED_CMO_ID);
                String cmoSampleId = sample.get(Constants.CMO_SAMPLE_ID);

                mappingFileContents += String.format("%s\t%s\n", cmoSampleId, correctedCmoId);
            }

            File mappingFile = new File(String.format("%s/%s_%s", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()), mappingFileName));

            PrintWriter pW = new PrintWriter(new FileWriter(mappingFile, false), false);
            filesCreated.add(mappingFile);
            pW.write(mappingFileContents);
            pW.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating mapping file: %s", mappingFileName), e);
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return true;
    }
}