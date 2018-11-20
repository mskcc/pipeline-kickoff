package org.mskcc.kickoff.printer;

import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mskcc.kickoff.config.Arguments.noPortal;
import static org.mskcc.kickoff.util.Utils.filterToAscii;

@Component
public class PortalConfPrinter extends FilePrinter {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);

    private final String manualMappingConfigMap = "name:ProjectTitle,desc:ProjectDesc,invest:PI,invest_name:PI_Name," +
            "tumor_type:TumorType,date_of_last_update:DateOfLastUpdate,assay_type:Assay";

    @Autowired
    public PortalConfPrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    @Override
    public boolean shouldPrint(KickoffRequest request) {
        return (!noPortal && request.getRequestType() != RequestType.RNASEQ) &&
                !(Utils.isExitLater()
                        && !request.isInnovation()
                        && request.getRequestType() != RequestType.OTHER
                        && request.getRequestType() != RequestType.RNASEQ);
    }

    @Override
    public String getFilePath(KickoffRequest request) {
        return String.format("%s/%s_portal_conf.txt", request.getOutputPath(), Utils
                .getFullProjectNameWithPrefix(request.getId()));
    }

    @Override
    public void register(ManifestFileObserver manifestFileObserver) {
        super.register(manifestFileObserver);
    }

    @Override
    public void print(KickoffRequest kickoffRequest) {
        DEV_LOGGER.info(String.format("Starting to create file: %s", getFilePath(kickoffRequest)));

        String requestFileContents = ((RequestFilePrinter) ManifestFile.REQUEST.getFilePrinter()).getFileContents();

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

        String replaceText = kickoffRequest.getRequestType().getName();
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
                    if (kickoffRequest.getRequestType() == RequestType.IMPACT) {
                        assay = parts[1].toUpperCase();
                    } else {
                        assay = parts[1];
                    }
                }
                configFileContents += configRequestMap.get(parts[0]) + "=\"" + parts[1] + "\"\n";
            }

            // Change path depending on where it should be going
            if (kickoffRequest.getRequestType() == RequestType.IMPACT || kickoffRequest.getRequestType() ==
                    RequestType.EXOME) {
                dataClinicalPath = String.valueOf(kickoffRequest.getOutputPath()).replaceAll("BIC/manifests", "CMO")
                        + "\n";
            } else {
                dataClinicalPath = String.valueOf(kickoffRequest.getOutputPath()).replaceAll("manifests",
                        replaceText) + "\n";
            }
        }
        // Now add all the fields that can't be grabbed from the request file
        configFileContents += "project=\"" + kickoffRequest.getId() + "\"\n";
        configFileContents += "groups=\"" + groups + "\"\n";
        configFileContents += "cna_seg=\"\"\n";
        configFileContents += "cna_seg_desc=\"Somatic CNA data (copy number ratio from tumor samples minus ratio from" +
                " matched normals).\"\n";
        configFileContents += "cna=\"\"\n";
        configFileContents += "maf=\"\"\n";
        configFileContents += "inst=\"cmo\"\n";
        configFileContents += "maf_desc=\"" + assay + " sequencing of tumor/normal samples\"\n";
        if (!dataClinicalPath.isEmpty()) {
            configFileContents += String.format("data_clinical=\"%s/%s_sample_data_clinical.txt\"\n",
                    dataClinicalPath, Utils.getFullProjectNameWithPrefix(kickoffRequest.getId()));
        } else {
            String message = String.format("Cannot find path to data clinical file: %s. Not included in portal " +
                    "config", dataClinicalPath);
            logWarning(message);
            configFileContents += "data_clinical=\"\"\n";
        }

        try {
            configFileContents = filterToAscii(configFileContents);
            PrintWriter pW = new PrintWriter(new FileWriter(new File(getFilePath(kickoffRequest)), false), false);
            pW.write(configFileContents);
            pW.close();
            observerManager.notifyObserversOfFileCreated(ManifestFile.PORTAL_CONF);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating portal config file: %s", getFilePath
                    (kickoffRequest)), e);
        }
    }

    public void logWarning(String message) {
        PM_LOGGER.log(PmLogPriority.WARNING, message);
        DEV_LOGGER.warn(message);
    }
}
