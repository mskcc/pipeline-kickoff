package org.mskcc.kickoff.printer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.RequestFileValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.mskcc.kickoff.config.Arguments.rerunReason;
import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.getJoinedCollection;

@Component
public class RequestFilePrinter extends FilePrinter {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final String manualMappingPinfoToRequestFile = "PI_Name:PI_Name," +
            "PI_E-mail:PI_E-mail,Investigator_E-mail:Investigator_E-mail,CMO_Project_ID:ProjectName," +
            "Final_Project_Title:ProjectTitle,CMO_Project_Brief:ProjectDesc";
    private ErrorRepository errorRepository;
    private RequestFileValidator requestFileValidator;
    @Value("${pipeline.name}")
    private String pipelineName;

    private String fileContents = "";

    @Autowired
    public RequestFilePrinter(ObserverManager observerManager, ErrorRepository errorRepository) {
        super(observerManager);
        this.errorRepository = errorRepository;
        this.requestFileValidator = new RequestFileValidator(observerManager);
    }

    @Override
    public void print(KickoffRequest request) {
        DEV_LOGGER.info(String.format("Starting to create file: %s", getFilePath(request)));

        Map<String, String> fieldNames = constructFieldNames();
        Map<String, String> fieldValues = constructFieldValues(request);

        this.requestFileValidator.test(fieldValues);

        StringBuilder requestFileContents = new StringBuilder();

        if (request.getRequestType() == RequestType.EXOME) {
            requestFileContents.append(String.format("Pipelines: %s\n", pipelineName));
            requestFileContents.append(String.format("Run_Pipeline: %s\n", pipelineName));
        } else if (request.getRequestType() == RequestType.IMPACT) {
            requestFileContents.append("Pipelines: dmp\n");
            requestFileContents.append("Run_Pipeline: dmp\n");
        } else if (request.getRequestType() == RequestType.RNASEQ) {
            requestFileContents.append("Run_Pipeline: rnaseq\n");
        } else if (request.getRecipe() == Recipe.CH_IP_SEQ) {
            requestFileContents.append("Run_Pipeline: chipseq\n");
        } else {
            requestFileContents.append("Run_Pipeline: other\n");
        }

        addProjectInfoContent(fieldNames, fieldValues, requestFileContents);
        requestFileContents.append("RunNumber: ").append(request.getRunNumbers()).append("\n");
        addRerunReason(request, requestFileContents);
        requestFileContents.append("RunID: ").append(getJoinedCollection(request.getRunIds(), ", ")).append("\n");

        requestFileContents.append("Institution: cmo\n");

        if (request.getRequestType() == RequestType.OTHER) {
            requestFileContents.append("Recipe: ").append(request.getRecipe() == null ? "" : request.getRecipe()
                    .getValue()).append("\n");
        }

        if (request.getRequestType() == RequestType.RNASEQ) {
            requestFileContents.append("AmplificationTypes: ").append(getJoinedCollection(request.getAmpTypes(), ", " +
                    "")).append("\n");
            requestFileContents.append("LibraryTypes: ").append(getJoinedLibTypes(request)).append("\n");

            if (request.getStrands().size() > 1) {
                String message = "Multiple strandedness options found!";
                logWarning(message);
            }

            requestFileContents.append("Strand: ").append(getJoinedCollection(request.getStrands(), ", ")).append("\n");
            requestFileContents.append("Pipelines: ");
            // If pipeline_options (command line) is not empty, put here. remember to remove the underscores
            // For now I am under the assumption that this is being passed correctly.
            // Default is Alignment STAR, Gene Count, Differential Gene Expression
            requestFileContents.append("NULL, RNASEQ_STANDARD_GENE_V1, RNASEQ_DIFFERENTIAL_GENE_V1");
            requestFileContents.append("\n");
        } else {
            requestFileContents.append("AmplificationTypes: NA\n");
            requestFileContents.append("LibraryTypes: NA\n");
            requestFileContents.append("Strand: NA\n");
        }

        // adding projectFolder back
        String projDir = request.getOutputPath();
        if (request.getRequestType() == RequestType.IMPACT || request.getRequestType() == RequestType.EXOME) {
            // @TODO generify path manifests
            requestFileContents.append("ProjectFolder: ").append(String.valueOf(projDir).replaceAll("BIC/manifests",
                    "CMO")).append("\n");
        } else {
            requestFileContents.append("ProjectFolder: ").append(String.valueOf(projDir).replaceAll("manifests", request
                    .getRequestType().getName())).append("\n");
        }

        // Date of last update
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        requestFileContents.append("DateOfLastUpdate: ").append(dateFormat.format(date)).append("\n");

        appendStrandErrorIfNeeded(request, requestFileContents);

        fileContents = requestFileContents.toString();

        try {
            requestFileContents = new StringBuilder(filterToAscii(fileContents));
            File requestFile = new File(getFilePath(request));
            PrintWriter pW = new PrintWriter(new FileWriter(requestFile, false), false);
            pW.write(requestFileContents.toString());
            pW.close();

            observerManager.notifyObserversOfFileCreated(ManifestFile.REQUEST);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating request file: %s", getFilePath(request)), e);
        }
    }

    public String getFileContents() {
        return fileContents;
    }

    private void appendStrandErrorIfNeeded(KickoffRequest request, StringBuilder requestFileContents) {
        if (errorRepository.getErrors().stream()
                .anyMatch(e -> e.getErrorCode() == ErrorCode.AMBIGUOUS_STRAND)) {
            requestFileContents
                    .append(String.format("Strand: *FATAL ERROR* multiple strand type in this project: %s",
                            request.getStrands()))
                    .append("\n");
        }
    }


    private Map<String, String> constructFieldNames() {
        Map<String, String> convertFieldNames = new LinkedHashMap<>();
        for (String conv : manualMappingPinfoToRequestFile.split(",")) {
            String[] parts = conv.split(":", 2);
            convertFieldNames.put(parts[0], parts[1]);
        }
        return convertFieldNames;
    }

    private Map<String, String> constructFieldValues(KickoffRequest request) {
        Map<String, String> projectInfo = request.getProjectInfo();
        Map<String, String> fieldValues = new HashMap<>(projectInfo);

        String piFirstName = projectInfo.get(Constants.ProjectInfo.PI_FIRSTNAME);
        String piLastName = projectInfo.get(Constants.ProjectInfo.PI_LASTNAME);

        String piEmail = projectInfo.get(Constants.ProjectInfo.PI_EMAIL);
        if (StringUtils.isEmpty(piEmail) || Constants.NA.equals(piEmail)) {
            fieldValues.put("PI_Name", projectInfo.get(Constants.ProjectInfo.LAB_HEAD));
            String labHeadEmail = projectInfo.get(Constants.ProjectInfo.LAB_HEAD_E_MAIL);
            fieldValues.put("PI_E-mail", labHeadEmail);
            fieldValues.put("PI", labHeadEmail.substring(0, labHeadEmail.indexOf("@")));
        } else {
            fieldValues.put("PI_Name", piLastName + ", " + piFirstName);
            String labHeadEmail = projectInfo.get(Constants.ProjectInfo.LAB_HEAD_E_MAIL);
            fieldValues.put("PI_E-mail", projectInfo.get(Constants.ProjectInfo.PI_EMAIL));
            fieldValues.put("PI", labHeadEmail.substring(0, labHeadEmail.indexOf("@")));
        }

        String investigatorEmail = projectInfo.get(Constants.ProjectInfo.INVESTIGATOR_EMAIL);
        fieldValues.put("Investigator_E-mail", investigatorEmail);

        if (investigatorEmail != null && investigatorEmail.contains("@")) {
            fieldValues.put("Investigator", investigatorEmail.substring(0, investigatorEmail.indexOf("@")));
        } else {
            fieldValues.put("Investigator", "");
        }

        request.setPi(fieldValues.get("PI"));
        request.setInvest(fieldValues.get("Investigator"));
        fieldValues.remove(Constants.ProjectInfo.LAB_HEAD);
        fieldValues.remove(Constants.ProjectInfo.LAB_HEAD_E_MAIL);
        fieldValues.remove(Constants.ProjectInfo.REQUESTOR);
        fieldValues.remove(Constants.ProjectInfo.REQUESTOR_E_MAIL);
        fieldValues.remove(Constants.ProjectInfo.PI_LASTNAME);
        fieldValues.remove(Constants.ProjectInfo.PI_FIRSTNAME);
        fieldValues.remove(Constants.ProjectInfo.PI_EMAIL);
        fieldValues.remove(Constants.ProjectInfo.CONTACT_NAME);
        return fieldValues;
    }

    private void addRerunReason(KickoffRequest request, StringBuilder requestFileContents) {
        if (request.getRunNumber() > 1) {
            String message = String.format("This project has been run before, rerun reason provided: %s", rerunReason);

            if (StringUtils.isEmpty(rerunReason)) {
                request.setRerunReason(Constants.DEFAULT_RERUN_REASON);
                message = String.format("This project has been run before, no rerun reason was provided thus using " +
                        "default value: %s", Constants.DEFAULT_RERUN_REASON);
            } else {
                request.setRerunReason(rerunReason);
            }

            DEV_LOGGER.info(message);
            PM_LOGGER.info(message);

            requestFileContents.append("Reason_for_rerun: ").append(request.getRerunReason()).append("\n");

        }
    }

    private void addProjectInfoContent(Map<String, String> fieldNames, Map<String, String> fieldValues,
                                       StringBuilder requestFileContents) {

        for (Map.Entry<String, String> property : fieldValues.entrySet()) {
            String propertyName = property.getKey();
            String value = property.getValue();

            if (value == null) {
                continue;
            }

            if (value.isEmpty() || Objects.equals(value, Constants.EMPTY)) {
                value = Constants.NA;
            }


            if (fieldNames.containsKey(propertyName)) {
                requestFileContents.append(fieldNames.get(propertyName)).append(": ").append(value).append("\n");
            } else if (propertyName.contains("IGO_Project_ID") || propertyName.equals(Constants.PROJECT_ID)) {
                requestFileContents.append("ProjectID: Proj_").append(value).append("\n");
            } else if (propertyName.contains("Platform") || propertyName.equals("Readme_Info") || propertyName.equals
                    ("Sample_Type") || propertyName.equals("Bioinformatic_Request")) {
                continue;
            } else if (propertyName.equals("Project_Manager")) {
                String[] tempName = value.split(", ");
                if (tempName.length > 1) {
                    requestFileContents.append(propertyName).append(": ").append(tempName[1]).append(" ").append
                            (tempName[0]).append("\n");
                } else {
                    requestFileContents.append(propertyName).append(": ").append(value).append("\n");
                }
            } else {
                requestFileContents.append(String.format("%s: %s\n", property.getKey(), property.getValue()));
            }
        }
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return String.format("%s/%s_request.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request
                .getId()));
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return true;
    }

    private String getJoinedLibTypes(KickoffRequest request) {
        return getJoinedCollection(request.getLibTypes(), ",");
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }

    public void logError(String message, Level pmLogLevel, Level devLogLevel) {
        Utils.setExitLater(true);
        PM_LOGGER.log(pmLogLevel, message);
        DEV_LOGGER.log(devLogLevel, message);
    }

}
