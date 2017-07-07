package org.mskcc.kickoff.lims;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.velox.util.VeloxConstants;
import org.mskcc.kickoff.velox.util.VeloxUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This class is an example standalone program.
 *
 * @author Krista Kaz
 */
public class QueryImpactProjectInfo {
    private static final Logger DEV_LOGGER = Logger.getLogger(QueryImpactProjectInfo.class);
    private static final LinkedHashMap<String, String> pmEmail = new LinkedHashMap<>();
    @Argument(alias = "p", description = "Project to get samples for")
    private static String project;
    private final HashSet<String> platforms = new HashSet<>();
    private Map<String, String> projectInfo = new LinkedHashMap<>();

    @Value("${limsConnectionFilePath}")
    private String limsConnectionFilePath;

    public static void main(String[] args) throws ServerException {
        QueryImpactProjectInfo qe = new QueryImpactProjectInfo();

        if (args.length == 0) {
            Args.usage(qe);
        } else {
            Args.parse(qe, args);
        }
    }

    public String getPI() {
        if (projectInfo.containsKey("Lab_Head_E-mail")) {
            return projectInfo.get("Lab_Head_E-mail");
        }
        return Constants.NULL;
    }

    public String getInvest() {
        if (projectInfo.containsKey("Requestor_E-mail")) {
            return projectInfo.get("Requestor_E-mail");
        }
        return Constants.NULL;
    }

    public Map<String, String> queryProjectInfo(Request request) {
        try {
            VeloxConnection connection = VeloxUtils.getVeloxConnection(limsConnectionFilePath);
            try {
                connection.open();
                VeloxStandalone.run(connection, new VeloxTask<Object>() {
                    @Override
                    public Object performTask() throws VeloxStandaloneException {
                        projectInfo = queryProjectInfo(user, dataRecordManager, request);
                        return new Object();
                    }
                });
            } finally {
                connection.close();
            }
        } catch (com.velox.sapioutils.client.standalone.VeloxConnectionException e) {
            System.err.println("com.velox.sapioutils.client.standalone.VeloxConnectionException: Connection refused for all users.1 ");
            System.out.println("[ERROR] There was an issue connecting with LIMs. Someone or something else may be using this connection. Please try again in a minute or two.");
            System.exit(0);
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage(), e);
        }

        return projectInfo;
    }

    public Map<String, String> queryProjectInfo(User apiUser, DataRecordManager drm, Request request) {
        // Adding PM emails to the hashmap
        pmEmail.put("Bouvier, Nancy", "bouviern@mskcc.org");
        pmEmail.put("Selcuklu, S. Duygu", "selcukls@mskcc.org");
        pmEmail.put("Bourque, Caitlin", "bourquec@mskcc.org");

        // If request ID includes anything besides A-Za-z_0-9 exit
        Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
        String requestID = request.getId();
        if (!reqPattern.matcher(requestID).matches()) {
            System.out.println("Malformed request ID.");
            return null;
        }

        try {
            List<DataRecord> requests = drm.queryDataRecords("Request", "RequestId = '" + requestID + "'", apiUser);
            List<String> ProjectFields = Arrays.asList("Lab_Head", "Lab_Head_E-mail", "Requestor", "Requestor_E-mail", "Platform", "Alternate_E-mails", "IGO_Project_ID", "Final_Project_Title", "CMO_Project_ID", "CMO_Project_Brief", "Project_Manager", "Readme_Info", "Data_Analyst", "Data_Analyst_E-mail");

            //initalizing empty map
            for (String field : ProjectFields) {
                projectInfo.put(field, Constants.NA);
            }

            String reqType = request.getRequestType();
            for (DataRecord requestDataRecord : requests) {
                if (reqType == null) {
                    reqType = figureOutReqType(requestDataRecord, apiUser, drm);
                }

                // Sample Preservation Types
                // Get samples - get their sample preservation
                List<DataRecord> Samps = Arrays.asList(requestDataRecord.getChildrenOfType(VeloxConstants.SAMPLE, apiUser));
                HashSet<String> preservations = new HashSet<>();
                if (Samps.size() > 0) {
                    List<Object> pres = drm.getValueList(Samps, VeloxConstants.PRESERVATION, apiUser);
                    for (Object pre : pres) {
                        String p = String.valueOf(pre).toUpperCase();
                        preservations.add(p);
                    }
                }
                // Get Samples that are in Plates
                for (DataRecord child : requestDataRecord.getChildrenOfType(VeloxConstants.PLATE, apiUser)) {
                    List<DataRecord> Samps2 = Arrays.asList(child.getChildrenOfType(VeloxConstants.SAMPLE, apiUser));
                    if (Samps2.size() > 0) {
                        List<Object> pres = drm.getValueList(Samps, VeloxConstants.PRESERVATION, apiUser);
                        for (Object pre : pres) {
                            String p = String.valueOf(pre).toUpperCase();
                            preservations.add(p);
                        }
                    }
                }
                projectInfo.put("Sample_Type", StringUtils.join(preservations, ","));

                if (!platforms.isEmpty()) {
                    projectInfo.put("Platform", StringUtils.join(platforms, ","));
                } else {
                    projectInfo.put("Platform", requestDataRecord.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser));
                }
                // ## Get Project Associated with it, print out necessary details
                List<DataRecord> parents = requestDataRecord.getParentsOfType(VeloxConstants.PROJECT, apiUser);
                if (parents != null && parents.size() > 0) {
                    DataRecord p = parents.get(0);

                    projectInfo.put("IGO_Project_ID", requestID);
                    projectInfo.put("Final_Project_Title", p.getStringVal("CMOFinalProjectTitle", apiUser));
                    projectInfo.put("CMO_Project_ID", p.getStringVal("CMOProjectID", apiUser));
                    // From projct brief you have to take out the \n.
                    projectInfo.put("CMO_Project_Brief", p.getStringVal("CMOProjectBrief", apiUser).replace("\n", "").replace("\r", ""));
                }

                projectInfo.put("Project_Manager", requestDataRecord.getPickListVal("ProjectManager", apiUser));
                projectInfo.put("Project_Manager_Email", String.valueOf(pmEmail.get(requestDataRecord.getPickListVal("ProjectManager", apiUser))));
                projectInfo.put("Readme_Info", requestDataRecord.getStringVal("ReadMe", apiUser));


                // ******************************* NEW FIELDS - will probably be moved
                // Bioinformatic Request: FASTQ and BICAnalysis (bools, request specific)
                projectInfo.put("Bioinformatic_Request", requestDataRecord.getPickListVal("DataDeliveryType", apiUser));

                // Data Analyst, Data Analyst E-mail
                projectInfo.put("Data_Analyst", requestDataRecord.getStringVal("DataAnalyst", apiUser));
                projectInfo.put("Data_Analyst_E-mail", requestDataRecord.getStringVal("DataAnalystEmail", apiUser));

                // ******************************* END OF NEW FIELDS

                // Values saved because they will be used to remove duplicated emails in email child
                String LabHeadEmail = requestDataRecord.getStringVal("LabHeadEmail", apiUser).toLowerCase();
                String InvestigatorEmail = requestDataRecord.getStringVal("Investigatoremail", apiUser).toLowerCase();
                String[] LabHeadName = WordUtils.capitalizeFully(requestDataRecord.getStringVal("LaboratoryHead", apiUser)).split(" ", 2);
                String[] RequesterName = WordUtils.capitalizeFully(requestDataRecord.getStringVal("Investigator", apiUser)).split(" ", 2);

                if (LabHeadName.length > 1) {
                    projectInfo.put("Lab_Head", LabHeadName[1] + ", " + LabHeadName[0]);
                }
                projectInfo.put("Lab_Head_E-mail", LabHeadEmail);
                if (RequesterName.length > 1) {
                    projectInfo.put("Requestor", RequesterName[1] + ", " + RequesterName[0]);
                }
                projectInfo.put("Requestor_E-mail", InvestigatorEmail);


                // Get the Child e-mail records, if there are any
                List<DataRecord> emailList = Arrays.asList(requestDataRecord.getChildrenOfType("Email", apiUser));
                if (emailList.size() > 0) {
                    List<String> allELists = new LinkedList<>();
                    for (DataRecord email : emailList) {
                        // Grab main email
                        String email_to_add = email.getStringVal("Email", apiUser).toLowerCase();
                        if (!email_to_add.equals(LabHeadEmail) &&
                                !email_to_add.equals(InvestigatorEmail) &&
                                !allELists.contains(email_to_add) && email_to_add.length() != 0) {
                            allELists.add(email_to_add);
                        }
                        // Grab AlternateEmail
                        email_to_add = email.getStringVal("AlternateEmail", apiUser).toLowerCase();
                        if (!email_to_add.equals(LabHeadEmail) &&
                                !email_to_add.equals(InvestigatorEmail) &&
                                !allELists.contains(email_to_add) && email_to_add.length() != 0) {
                            allELists.add(email_to_add);
                        }

                    }
                    if (allELists.size() > 0) {
                        String AlternateEmails = StringUtils.join(allELists, ",");
                        projectInfo.put("Alternate_E-mails", AlternateEmails);
                    }
                }
            }

            return getTransformedProjectInfo(projectInfo);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving project info for request: %s", requestID), e);
        }

        return Collections.emptyMap();
    }

    private String figureOutReqType(DataRecord request, User apiUser, DataRecordManager drm) {
        String reqType = Constants.NULL;
        try {
            List<DataRecord> nymList = request.getDescendantsOfType(VeloxConstants.NIMBLE_GEN_HYB_PROTOCOL, apiUser);
            if (request.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser).contains("PACT") || nymList.size() != 0) {
                reqType = Constants.IMPACT;

                // For each Nimb record in nymb list, get capture bait set and spike in
                if (nymList.size() > 0) {
                    List<Object> baitSets = drm.getValueList(nymList, VeloxConstants.RECIPE, apiUser);
                    List<Object> spikeIn = drm.getValueList(nymList, VeloxConstants.SPIKE_IN_GENES, apiUser);

                    for (int i = 0; i < baitSets.size(); i++) {
                        String b = String.valueOf(baitSets.get(i));
                        if (b.equals(Constants.NULL) || b.isEmpty()) {
                            continue;
                        }
                        String s = String.valueOf(spikeIn.get(i));
                        if (s.equals(Constants.NULL) || s.isEmpty()) {
                            platforms.add(b);
                        } else {
                            platforms.add(b + "+" + s);
                        }
                    }
                }
            } else {
                List<DataRecord> kapaList = request.getDescendantsOfType(VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_1, apiUser);
                String requestName = request.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser);
                if (requestName.contains("Exome") || requestName.equals("WES") || kapaList.size() != 0) {
                    reqType = Constants.EXOME;
                    // For each kapa record, grab the capture type
                    List<Object> baitSets = drm.getValueList(kapaList, VeloxConstants.AGILENT_CAPTURE_KIT, apiUser);
                    for (Object baitSet : baitSets) {
                        String b = String.valueOf(baitSet);
                        if (b.equals(Constants.NULL) || b.isEmpty()) {
                            continue;
                        }
                        platforms.add(b);
                    }
                }
            }
        } catch (Exception e) {
            DEV_LOGGER.warn("Exception thrown while retrieving request type for request", e);
        }

        return reqType;
    }

    private Map<String, String> getTransformedProjectInfo(Map<String, String> projectInfo) {
        Map<String, String> transformedProjectInfo = new HashMap<>();
        for (Map.Entry<String, String> entry : projectInfo.entrySet()) {
            String val = entry.getValue();
            if (val.equals(Constants.NULL) || val.isEmpty()) {
                val = Constants.NA;
            }
            String transformedKey = Utils.filterToAscii(entry.getKey());
            String transformedValue = Utils.filterToAscii(val);
            transformedProjectInfo.put(transformedKey, transformedValue);
        }

        return transformedProjectInfo;
    }

}


