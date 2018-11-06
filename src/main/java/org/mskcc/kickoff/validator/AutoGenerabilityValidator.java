package org.mskcc.kickoff.validator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
class AutoGenerabilityValidator {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final ErrorRepository errorRepository;

    @Autowired
    public AutoGenerabilityValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    public void validate(KickoffRequest kickoffRequest) {
        boolean autoGenAble = kickoffRequest.isBicAutorunnable();

        if (!autoGenAble) {
            String reason = "";
            String readMe = kickoffRequest.getReadMe();
            String[] bicReadmeLines = readMe.split("\\n\\r|\\n");
            for (String bicLine : bicReadmeLines) {
                if (bicLine.startsWith("NOT_AUTORUNNABLE")) {
                    reason = "REASON: " + bicLine;
                    break;
                }
            }
            String requestID = kickoffRequest.getId();
            String message = String.format("According to the LIMS, project %s cannot be run through this script. %s " +
                    "Sorry :(", requestID, reason);
            Utils.setExitLater(true);
            PM_LOGGER.log(Level.ERROR, message);

            String msg = String.format("Bic Autorunnable option is not chosen in LIMS. Request id: %s cannot " +
                    "be run automatically", requestID);

            DEV_LOGGER.log(Level.ERROR, msg);
            errorRepository.add(new GenerationError(msg, ErrorCode.NOT_AUTORUNNABLE));

            throw new RuntimeException(msg);
        }
    }
}
