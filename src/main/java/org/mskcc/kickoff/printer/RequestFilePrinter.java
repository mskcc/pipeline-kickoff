package org.mskcc.kickoff.printer;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Recipe;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.mskcc.kickoff.config.Arguments.*;
import static org.mskcc.kickoff.printer.OutputFilesPrinter.filesCreated;
import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.getJoinedCollection;

public class RequestFilePrinter implements FilePrinter {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final String manualMappingPinfoToRequestFile = "Alternate_E-mails:DeliverTo,Lab_Head:PI_Name,Lab_Head_E-mail:PI,Requestor:Investigator_Name,Requestor_E-mail:Investigator,CMO_Project_ID:ProjectName,Final_Project_Title:ProjectTitle,CMO_Project_Brief:ProjectDesc";
    private final String manualMappingConfigMap = "name:ProjectTitle,desc:ProjectDesc,invest:PI,invest_name:PI_Name,tumor_type:TumorType,date_of_last_update:DateOfLastUpdate,assay_type:Assay";

    public void print(Request request) {
        // This will change the fields of the pInfo array, and print out the correct field
        // It will also pr/int all the ampType and libTypes, and species.
        String requestFileContents = "";

        if (request.getRequestType().equals(Constants.EXOME)) {
            requestFileContents += "Pipelines: variants\n";
            requestFileContents += "Run_Pipeline: variants\n";
        } else if (request.getRequestType().equals(Constants.IMPACT)) {
            requestFileContents += "Pipelines: \n";
            requestFileContents += "Run_Pipeline: \n";
        } else if (request.getRequestType().equals(Constants.RNASEQ)) {
            requestFileContents += "Run_Pipeline: rnaseq\n";
        } else if (request.getRecipe().size() == 1 && request.getRecipe().get(0) == Recipe.CH_IP_SEQ) {
            requestFileContents += "Run_Pipeline: chipseq\n";
        } else {
            requestFileContents += "Run_Pipeline: other\n";
        }

        // This is quickly generating a map from old name to new name (validator takes old name)
        Map<String, String> convertFieldNames = new LinkedHashMap<>();
        for (String conv : manualMappingPinfoToRequestFile.split(",")) {
            String[] parts = conv.split(":", 2);
            convertFieldNames.put(parts[0], parts[1]);
        }
        // Now go through pInfo, and swap out the old name for the new name, and print
        for (Map.Entry<String, String> property : request.getProjectInfo().entrySet()) {
            String propertyName = property.getKey();
            String value = property.getValue();

            if (value == null)
                continue;
            if (value.isEmpty() || Objects.equals(value, Constants.EMPTY))
                value = Constants.NA;

            if (convertFieldNames.containsKey(propertyName)) {
                if (propertyName.endsWith("_E-mail")) {
                    if (propertyName.equals("Requestor_E-mail")) {
                        requestFileContents += "Investigator_E-mail: " + value + "\n";
                    }
                    if (propertyName.equals("Lab_Head_E-mail")) {
                        requestFileContents += "PI_E-mail: " + value + "\n";
                    }
                    String[] temp = value.split("@");
                    value = temp[0];
                }
                requestFileContents += convertFieldNames.get(propertyName) + ": " + value + "\n";
            } else if (propertyName.contains("IGO_Project_ID") || propertyName.equals(Constants.PROJECT_ID)) {
                requestFileContents += "ProjectID: Proj_" + value + "\n";
            } else if (propertyName.contains("Platform") || propertyName.equals("Readme_Info") || propertyName.equals("Sample_Type") || propertyName.equals("Bioinformatic_Request")) {
                continue;
            } else if (propertyName.equals("Project_Manager")) {
                String[] tempName = value.split(", ");
                if (tempName.length > 1) {
                    requestFileContents += propertyName + ": " + tempName[1] + " " + tempName[0] + "\n";
                } else {
                    requestFileContents += propertyName + ": " + value + "\n";
                }
            } else {
                requestFileContents += String.format("%s: %s\n", property.getKey(), property.getValue());
            }
            if (propertyName.equals("Requestor_E-mail")) {
                request.setInvest(value);
            }
            if (propertyName.equals("Lab_Head_E-mail")) {
                request.setPi(value);
            }
        }

        if (request.getInvest().isEmpty() || request.getPi().isEmpty()) {
            logError(String.format("Cannot create run number because PI and/or Investigator is missing. %s %s", request.getPi(), request.getInvest()), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
        } else {
            if (request.getRunNumber() != 0) {
                requestFileContents += "RunNumber: " + request.getRunNumber() + "\n";
            }
        }
        if (request.getRunNumber() > 1) {
            if (StringUtils.isEmpty(rerunReason)) {
                request.setRerunReason(Constants.DEFAULT_RERUN_REASON);
                String message = String.format("This project has been run before, no rerun reason was provided this using default value: %s", Constants.DEFAULT_RERUN_REASON);
                DEV_LOGGER.info(message);
                PM_LOGGER.info(message);
            } else {
                request.setRerunReason(rerunReason);
                String message = String.format("This project has been run before, rerun reason provided: %s", rerunReason);
                DEV_LOGGER.info(message);
                PM_LOGGER.info(message);
            }
            requestFileContents += "Reason_for_rerun: " + request.getRerunReason() + "\n";

        }
        requestFileContents += "RunID: " + getJoinedCollection(request.getRunIdList(), ", ") + "\n";

        requestFileContents += "Institution: cmo\n";

        if (Objects.equals(request.getRequestType(), Constants.OTHER)) {
            requestFileContents += "Recipe: " + Utils.getJoinedCollection(request.getRecipe(), ",") + "\n";
        }

        if (Objects.equals(request.getRequestType(), Constants.RNASEQ)) {
            requestFileContents += "AmplificationTypes: " + getJoinedCollection(request.getAmpType(), ", ") + "\n";
            requestFileContents += "LibraryTypes: " + getJoinedLibTypes(request) + "\n";

            if (request.getStrands().size() > 1) {
                String message = "Multiple strandedness options found!";
                logWarning(message);
            }

            requestFileContents += "Strand: " + getJoinedCollection(request.getStrands(), ", ") + "\n";
            requestFileContents += "Pipelines: ";
            // If pipeline_options (command line) is not empty, put here. remember to remove the underscores
            // For now I am under the assumption that this is being passed correctly.
            // Default is Alignment STAR, Gene Count, Differential Gene Expression
            requestFileContents += "NULL, RNASEQ_STANDARD_GENE_V1, RNASEQ_DIFFERENTIAL_GENE_V1";
            requestFileContents += "\n";
        } else {
            requestFileContents += "AmplificationTypes: NA\n";
            requestFileContents += "LibraryTypes: NA\n";
            requestFileContents += "Strand: NA\n";
        }

        // adding projectFolder back
        String projDir = request.getOutputPath();
        if (request.getRequestType().equals(Constants.IMPACT) || request.getRequestType().equals(Constants.EXOME)) {
            requestFileContents += "ProjectFolder: " + String.valueOf(projDir).replaceAll("BIC/drafts", "CMO") + "\n";
        } else {
            requestFileContents += "ProjectFolder: " + String.valueOf(projDir).replaceAll("drafts", request.getRequestType()) + "\n";
        }

        // Date of last update
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        requestFileContents += "DateOfLastUpdate: " + dateFormat.format(date) + "\n";

        if ((!noPortal && !request.getRequestType().equals(Constants.RNASEQ)) &&
                !(Utils.isExitLater()
                        && !krista && !request.isInnovationProject()
                        && !request.getRequestType().equals(Constants.OTHER)
                        && !request.getRequestType().equals(Constants.RNASEQ))) {
            printPortalConfig(requestFileContents, request);
        }

        try {
            requestFileContents = filterToAscii(requestFileContents);
            File requestFile = new File(projDir + "/" + Utils.getFullProjectNameWithPrefix(request.getId()) + "_request.txt");
            PrintWriter pW = new PrintWriter(new FileWriter(requestFile, false), false);
            filesCreated.add(requestFile);
            pW.write(requestFileContents);
            pW.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating request file: %s", requestFileContents), e);
        }
    }

    @Override
    public boolean shouldPrint(Request request) {
        return true;
    }

    private String getJoinedLibTypes(Request request) {
        return getJoinedCollection(request.getLibTypes(), ",");
    }

    private void printPortalConfig(String requestFileContents, Request request) {
        // First make map from request to portal config
        // THen create final map of portal config with values.
        Map<String, String> configRequestMap = new LinkedHashMap<>();
        for (String conv : manualMappingConfigMap.split(",")) {
            String[] parts = conv.split(":", 2);
            configRequestMap.put(parts[1], parts[0]);
        }
        String groups = "COMPONC;";
        String assay = "";
        String dataClinicalPath = "";

        String replaceText = request.getRequestType();
        if (replaceText.equals(Constants.EXOME)) {
            replaceText = "variant";
        }
        // For each line of requestFileContents, grab any fields that are in the map
        // and add them to the configFileContents variable.
        String configFileContents = "";
        for (String line : requestFileContents.split("\n")) {
            String[] parts = line.split(": ", 2);
            if (configRequestMap.containsKey(parts[0])) {
                if (parts[0].equals("PI")) {
                    groups += parts[1].toUpperCase();
                }
                if (parts[0].equals("Assay")) {
                    if (Objects.equals(request.getRequestType(), Constants.IMPACT)) {
                        assay = parts[1].toUpperCase();
                    } else {
                        assay = parts[1];
                    }
                }
                configFileContents += configRequestMap.get(parts[0]) + "=\"" + parts[1] + "\"\n";
            }

            // Change path depending on where it should be going
            if (request.getRequestType().equals(Constants.IMPACT) || request.getRequestType().equals(Constants.EXOME)) {
                dataClinicalPath = String.valueOf(request.getOutputPath()).replaceAll("BIC/drafts", "CMO") + "\n";
            } else {
                dataClinicalPath = String.valueOf(request.getOutputPath()).replaceAll("drafts", replaceText) + "\n";
            }
        }
        // Now add all the fields that can't be grabbed from the request file
        configFileContents += "project=\"" + request.getId() + "\"\n";
        configFileContents += "groups=\"" + groups + "\"\n";
        configFileContents += "cna_seg=\"\"\n";
        configFileContents += "cna_seg_desc=\"Somatic CNA data (copy number ratio from tumor samples minus ratio from matched normals).\"\n";
        configFileContents += "cna=\"\"\n";
        configFileContents += "maf=\"\"\n";
        configFileContents += "inst=\"cmo\"\n";
        configFileContents += "maf_desc=\"" + assay + " sequencing of tumor/normal samples\"\n";
        if (!dataClinicalPath.isEmpty()) {
            configFileContents += String.format("data_clinical=\"%s/%s_sample_data_clinical.txt\"\n", dataClinicalPath, Utils.getFullProjectNameWithPrefix(request.getId()));
        } else {
            String message = String.format("Cannot find path to data clinical file: %s. Not included in portal config", dataClinicalPath);
            logWarning(message);
            configFileContents += "data_clinical=\"\"\n";
        }
        File configFile = null;

        try {
            configFileContents = filterToAscii(configFileContents);
            configFile = new File(String.format("%s/%s_portal_conf.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId())));
            filesCreated.add(configFile);
            PrintWriter pW = new PrintWriter(new FileWriter(configFile, false), false);
            pW.write(configFileContents);
            pW.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating portal config file: %s", configFile), e);
        }
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
