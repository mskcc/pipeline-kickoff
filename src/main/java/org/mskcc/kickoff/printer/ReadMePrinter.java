package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.kickoff.util.Utils.filterToAscii;

public class ReadMePrinter implements FilePrinter  {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public void print(Request request) {
        String readMeInfo = request.getReadmeInfo();
        if (readMeInfo.length() > 0) {
            File readmeFile = null;
            try {
                readMeInfo = filterToAscii(readMeInfo);
                readmeFile = new File(request.getOutputPath() + "/" + Utils.getFullProjectNameWithPrefix(request.getId()) + "_README.txt");
                PrintWriter pW = new PrintWriter(new FileWriter(readmeFile, false), false);
                filesCreated.add(readmeFile);
                pW.write(readMeInfo + "\n");
                pW.close();
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while creating readme file: %s", readmeFile), e);
            }
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return !(Utils.isExitLater()
                && !krista && !request.isInnovationProject()
                && !request.getRequestType().equals(Constants.OTHER)
                && !request.getRequestType().equals(Constants.RNASEQ));
    }
}