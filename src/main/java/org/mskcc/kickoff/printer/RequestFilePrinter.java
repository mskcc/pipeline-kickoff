package org.mskcc.kickoff.printer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
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
import static org.mskcc.kickoff.util.Utils.filterToAscii;
import static org.mskcc.kickoff.util.Utils.getJoinedCollection;

public class RequestFilePrinter implements FilePrinter {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final String manualMappingPinfoToRequestFile = "Alternate_E-mails:DeliverTo,Lab_Head:PI_Name,Lab_Head_E-mail:PI,Requestor:Investigator_Name,Requestor_E-mail:Investigator,CMO_Project_ID:ProjectName,Final_Project_Title:ProjectTitle,CMO_Project_Brief:ProjectDesc";
    private final String manualMappingConfigMap = "name:ProjectTitle,desc:ProjectDesc,invest:PI,invest_name:PI_Name,tumor_type:TumorType,date_of_last_update:DateOfLastUpdate,assay_type:Assay";

    public void print(KickoffRequest request) {
        // This will change the fields of the pInfo array, and print out the correct field
        // It will also pr/int all the ampType and libTypes, and species.
        StringBuilder requestFileContents = new StringBuilder();

        if (request.getRequestType() == RequestType.EXOME) {
            requestFileContents.append("Pipelines: variants\n");
            requestFileContents.append("Run_Pipeline: variants\n");
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
                        requestFileContents.append("Investigator_E-mail: ").append(value).append("\n");
                    }
                    if (propertyName.equals("Lab_Head_E-mail")) {
                        requestFileContents.append("PI_E-mail: ").append(value).append("\n");
                    }
                    String[] temp = value.split("@");
                    value = temp[0];
                }
                requestFileContents.append(convertFieldNames.get(propertyName)).append(": ").append(value).append("\n");
            } else if (propertyName.contains("IGO_Project_ID") || propertyName.equals(Constants.PROJECT_ID)) {
                requestFileContents.append("ProjectID: Proj_").append(value).append("\n");
            } else if (propertyName.contains("Platform") || propertyName.equals("Readme_Info") || propertyName.equals("Sample_Type") || propertyName.equals("Bioinformatic_Request")) {
                continue;
            } else if (propertyName.equals("Project_Manager")) {
                String[] tempName = value.split(", ");
                if (tempName.length > 1) {
                    requestFileContents.append(propertyName).append(": ").append(tempName[1]).append(" ").append(tempName[0]).append("\n");
                } else {
                    requestFileContents.append(propertyName).append(": ").append(value).append("\n");
                }
            } else {
                requestFileContents.append(String.format("%s: %s\n", property.getKey(), property.getValue()));
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
            if (!Objects.equals(request.getRunNumbers(), "0")) {
                requestFileContents.append("RunNumber: ").append(request.getRunNumbers()).append("\n");
            }
        }
        if (request.getRunNumber() > 1) {
            if (!shiny && (rerunReason == null || rerunReason.isEmpty())) {
                logError("This generation of the project files is a rerun, but no rerun reason was given. Use option 'rerunReason' to give a reason for this rerun in quotes. ", PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                Utils.setExitLater(true);
                return;
            }
            requestFileContents.append("Reason_for_rerun: ").append(rerunReason).append("\n");

        }
        requestFileContents.append("RunID: ").append(getJoinedCollection(request.getRunIds(), ", ")).append("\n");

        requestFileContents.append("Institution: cmo\n");

        if (request.getRequestType() == RequestType.OTHER) {
            requestFileContents.append("Recipe: ").append(request.getRecipe() == null ? "" : request.getRecipe().getValue()).append("\n");
        }

        if (request.getRequestType() == RequestType.RNASEQ) {
            requestFileContents.append("AmplificationTypes: ").append(getJoinedCollection(request.getAmpTypes(), ", ")).append("\n");
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
            requestFileContents.append("ProjectFolder: ").append(String.valueOf(projDir).replaceAll("BIC/drafts", "CMO")).append("\n");
        } else {
            requestFileContents.append("ProjectFolder: ").append(String.valueOf(projDir).replaceAll("drafts", request.getRequestType().getName())).append("\n");
        }

        // Date of last update
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        requestFileContents.append("DateOfLastUpdate: ").append(dateFormat.format(date)).append("\n");

        if ((!noPortal && request.getRequestType() != RequestType.RNASEQ) &&
                !(Utils.isExitLater()
                        && !krista && !request.isInnovation()
                        && request.getRequestType() != RequestType.OTHER
                        && request.getRequestType() != RequestType.RNASEQ)) {
            printPortalConfig(requestFileContents.toString(), request);
        }

        try {
            requestFileContents = new StringBuilder(filterToAscii(requestFileContents.toString()));
            File requestFile = new File(getFileName(request));
            PrintWriter pW = new PrintWriter(new FileWriter(requestFile, false), false);
            pW.write(requestFileContents.toString());
            pW.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating request file: %s", getFileName(request)), e);
        }
    }

    private String getFileName(KickoffRequest request) {
        return String.format("%s/%s_request.txt", request.getOutputPath(), Utils.getFullProjectNameWithPrefix(request.getId()));
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return true;
    }

    private String getJoinedLibTypes(KickoffRequest request) {
        return getJoinedCollection(request.getLibTypes(), ",");
    }

    private void printPortalConfig(String requestFileContents, KickoffRequest request) {
        // First make map from request to portal config
        // THen create final map of portal config with values.
        Map<String, String> configRequestMap = new LinkedHashMap<>();
        for (String conv : manualMappingConfigMap.split(",")) {
            String[] parts = conv.split(":", 2);
            configRequestMap.put(parts[1], parts[0]);
        }
        StringBuilder groups = new StringBuilder("COMPONC;");
        String assay = "";
        String dataClinicalPath = "";

        String replaceText = request.getRequestType().getName();
        if (replaceText.equalsIgnoreCase(Constants.EXOME)) {
            replaceText = "variant";
        }
        // For each line of requestFileContents, grab any fields that are in the map
        // and add them to the configFileContents variable.
        String configFileContents = "";
        for (String line : requestFileContents.split("\n")) {
            String[] parts = line.split(": ", 2);
            if (configRequestMap.containsKey(parts[0])) {
                if (parts[0].equals("PI")) {
                    groups.append(parts[1].toUpperCase());
                }
                if (parts[0].equals("Assay")) {
                    if (request.getRequestType() == RequestType.IMPACT) {
                        assay = parts[1].toUpperCase();
                    } else {
                        assay = parts[1];
                    }
                }
                configFileContents += configRequestMap.get(parts[0]) + "=\"" + parts[1] + "\"\n";
            }

            // Change path depending on where it should be going
            if (request.getRequestType() == RequestType.IMPACT || request.getRequestType() == RequestType.EXOME) {
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
