package org.mskcc.kickoff.roslin.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.roslin.util.Constants;
import org.mskcc.kickoff.roslin.util.Utils;

import java.io.File;
import java.util.function.Predicate;

public class OutputDirValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        boolean valid = true;
        //@TODO after finishing comparing with current prod version refactor it to check outdir only once, as for now
        // it stays fo tests to pass
        if (!StringUtils.isEmpty(Arguments.outdir)) {
            File outputDir = new File(Arguments.outdir);

            if (!StringUtils.isEmpty(Arguments.outdir)) {
                if (outputDir.exists() && outputDir.isDirectory()) {
                    String message = String.format("Overwriting default dir to %s", kickoffRequest.getOutputPath());
                    PM_LOGGER.info(message);
                    DEV_LOGGER.info(message);
                } else {
                    String message = String.format("The outdir directory given is empty or does not exist: %s",
                            Arguments.outdir);
                    Utils.setExitLater(true);
                    PM_LOGGER.error(message);
                    DEV_LOGGER.error(message);
                    valid = false;
                }
            }
        }

        return valid;
    }
}
