package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

import static org.mskcc.kickoff.util.Utils.filterToAscii;

@Component
public class ReadMePrinter extends FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Autowired
    public ReadMePrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    public void print(KickoffRequest request) {
        DEV_LOGGER.info(String.format("Starting to create file: %s", getFilePath(request)));

        String readMeInfo = request.getReadmeInfo();
        if (readMeInfo.length() > 0) {
            File readmeFile = null;
            try {
                readMeInfo = filterToAscii(readMeInfo);
                readmeFile = new File(getFilePath(request));
                PrintWriter pW = new PrintWriter(new FileWriter(readmeFile, false), false);
                pW.write(readMeInfo + "\n");
                pW.close();
            } catch (Exception e) {
                DEV_LOGGER.warn(String.format("Exception thrown while creating readme file: %s", readmeFile), e);
            }
        }
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return request.getOutputPath() + "/" + Utils.getFullProjectNameWithPrefix(request.getId()) + "_README.txt";
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return !(Utils.isExitLater()
                && !request.isInnovation()
                && request.getRequestType() != RequestType.OTHER
                && request.getRequestType() != RequestType.RNASEQ);
    }
}
