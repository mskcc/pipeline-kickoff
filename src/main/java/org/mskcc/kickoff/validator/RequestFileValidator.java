package org.mskcc.kickoff.validator;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class RequestFileValidator implements Predicate<Map<String, String>> {

    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private Predicate<String> notNullString = s -> s != null;
    private Predicate<String> notBlankString = StringUtils::isNotBlank;
    private Predicate<String> naString = s -> Constants.NA.equals(s);
    private Predicate<String> emptyKapaAssay = s ->
            Constants.NoKAPACaptureProtocol1.equals(s) || Constants.NoKAPACaptureProtocol2.equals(s);
    private Predicate<String> validEmail = s ->
            Pattern.compile("^(.+)@[a-zA-Z0-9-]+\\.[a-zA-Z0-9.]{2,}$", Pattern.CASE_INSENSITIVE)
                    .matcher(s.toLowerCase()).matches();
    private Predicate<String> validMskccEmail = s ->
            Pattern.compile("^(.+)@(mskcc\\.org|sloankettering\\.edu)$", Pattern.CASE_INSENSITIVE)
                    .matcher(s.toLowerCase()).matches();

    private static final String[] requiredFields = new String[]{
            Constants.ProjectInfo.ASSAY, "PI_E-mail", "Investigator_E-mail"
    };

    private ObserverManager observerManager;

    public RequestFileValidator(ObserverManager observerManager) {
        this.observerManager = observerManager;
    }

    @Override
    public boolean test(Map<String, String> fieldValues) {
        String requestId = fieldValues.get(Constants.ProjectInfo.IGO_PROJECT_ID);
        String pm = fieldValues.get(Constants.ProjectInfo.PROJECT_MANAGER);
        List<String> errors = new ArrayList<>();
        for (String requiredField : requiredFields) {
            String fieldValue = fieldValues.get(requiredField);
            switch (requiredField) {
                case Constants.ProjectInfo.ASSAY:
                    if (!validateAssay(fieldValue)) {
                        String message = String.format("No %s available for request %s (value found: %s).",
                                requiredField, requestId, fieldValue);
                        DEV_LOGGER.error(message);
                        errors.add(message);
                    }
                    break;
                case "PI_E-mail":
                case "Investigator_E-mail":
                    boolean isCmoSide = Utils.isCmoSideProject(pm);
                    if (!validateEmail(isCmoSide, fieldValue)) {
                        String message = String.format("Invalid email%s for %s (value found: %s).",
                               isCmoSide ? "" : "(mskcc)", requiredField, fieldValue);
                        DEV_LOGGER.error(message);
                        errors.add(message);
                    }
                    break;
            }
        }

        if (!errors.isEmpty()) {
            observerManager.notifyObserversOfError(ManifestFile.REQUEST, new GenerationError(
                    String.join("; ", errors), ErrorCode.REQUEST_INFO_MISSING));
        }

        return errors.isEmpty();
    }

    private boolean validateAssay(String assay) {
        return notBlankString.and(naString.negate())
                .and(emptyKapaAssay.negate())
                .test(assay);
    }

    private boolean validateEmail(boolean isCmoSide, String email) {
        if (isCmoSide) {
            return notNullString.and(validEmail).test(email);
        } else {
            return notNullString.and(validMskccEmail).test(email);
        }
    }
}
