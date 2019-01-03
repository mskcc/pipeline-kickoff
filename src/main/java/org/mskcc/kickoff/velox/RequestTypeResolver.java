package org.mskcc.kickoff.velox;

import org.apache.log4j.Logger;
import org.mskcc.domain.*;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;

import static org.mskcc.kickoff.config.Arguments.runAsExome;

public class RequestTypeResolver {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    public RequestType resolve(Request kickoffRequest) {
        RequestType requestType = RequestType.OTHER;

        if (kickoffRequest.getRequestType() == null) {
            // Here I will pull the childs field recipe
            Recipe recipe = kickoffRequest.getRecipe();
            logWarning(String.format("RECIPE for request %s is: %s", kickoffRequest.getId(), kickoffRequest.getRecipe
                    ()));
            if (kickoffRequest.getName().matches("(.*)PACT(.*)"))
                requestType = RequestType.IMPACT;

            if (kickoffRequest.getName().matches("(.*)WES(.*)"))
                requestType = RequestType.EXOME;

            if (isSmarterAmpSeqRecipe(recipe)) {
                kickoffRequest.getLibTypes().add(LibType.SMARTER_AMPLIFICATION);
                kickoffRequest.getStrands().add(Strand.NONE);
                requestType = RequestType.RNASEQ;
            }
            if (kickoffRequest.getRequestType() == null) {
                if (kickoffRequest.isInnovation()) {
                    logWarning("05500 project. This should be pulled as an impact.");
                    requestType = RequestType.IMPACT;
                } else if (runAsExome) {
                    requestType = RequestType.EXOME;
                } else {
                    logWarning(String.format("Request Name %s doesn't match one of the supported request types. Set " +
                                    "request type as OTHER: information will be pulled as if it is an rnaseq/unknown " +
                            "run.", kickoffRequest.getName()));
                    requestType = RequestType.OTHER;
                }
            }
        }

        return requestType;
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }

    private boolean isSmarterAmpSeqRecipe(Recipe recipe) {
        return recipe == Recipe.SMARTER_AMP_SEQ;
    }
}
