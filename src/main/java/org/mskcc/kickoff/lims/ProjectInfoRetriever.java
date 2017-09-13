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
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.velox.util.VeloxUtils;
import org.mskcc.util.VeloxConstants;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This class is an example standalone program.
 *
 * @author Krista Kaz
 */
public class ProjectInfoRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(ProjectInfoRetriever.class);
    private static final LinkedHashMap<String, String> pmEmail = new LinkedHashMap<>();
    @Argument(alias = "p", description = "Project to get samples for")
    private final HashSet<String> platforms = new HashSet<>();
    private Map<String, String> projectInfo = new LinkedHashMap<>();

    @Value("${limsConnectionFilePath}")
    private String limsConnectionFilePath;

    public static void main(String[] args) throws ServerException {
        ProjectInfoRetriever qe = new ProjectInfoRetriever();

        if (args.length == 0) {
            Args.usage(qe);
        } else {
            Args.parse(qe, args);
        }
    }

    public String getPI() {
        return projectInfo.getOrDefault(Constants.ProjectInfo.LAB_HEAD_E_MAIL, Constants.NA);
    }

    public String getInvest() {
        return projectInfo.getOrDefault(Constants.ProjectInfo.REQUESTOR_E_MAIL, Constants.NA);
    }

    public Map<String, String> queryProjectInfo(KickoffRequest request) {
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

    public Map<String, String> queryProjectInfo(User apiUser, DataRecordManager drm, KickoffRequest kickoffRequest) {
        // Adding PM emails to the hashmap
        pmEmail.put("Bouvier, Nancy", "bouviern@mskcc.org");
        pmEmail.put("Selcuklu, S. Duygu", "selcukls@mskcc.org");
        pmEmail.put("Bourque, Caitlin", "bourquec@mskcc.org");

        // If request ID includes anything besides A-Za-z_0-9 exit
        Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
        String requestID = kickoffRequest.getId();
        if (!reqPattern.matcher(requestID).matches()) {
            DEV_LOGGER.error("Malformed request ID.");
            return null;
        }

        try {
            List<DataRecord> requests = drm.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" + requestID + "'", apiUser);
            List<String> ProjectFields = Arrays.asList(
                    Constants.ProjectInfo.LAB_HEAD,
                    Constants.ProjectInfo.LAB_HEAD_E_MAIL,
                    Constants.ProjectInfo.REQUESTOR,
                    Constants.ProjectInfo.REQUESTOR_E_MAIL,
                    Constants.ProjectInfo.PLATFORM,
                    Constants.ProjectInfo.ALTERNATE_EMAILS,
                    Constants.ProjectInfo.IGO_PROJECT_ID,
                    Constants.ProjectInfo.FINAL_PROJECT_TITLE,
                    Constants.ProjectInfo.CMO_PROJECT_ID,
                    Constants.ProjectInfo.CMO_PROJECT_BRIEF,
                    Constants.ProjectInfo.PROJECT_MANAGER,
                    Constants.ProjectInfo.README_INFO,
                    Constants.ProjectInfo.DATA_ANALYST,
                    Constants.ProjectInfo.DATA_ANALYST_EMAIL);

            //initalizing empty map
            for (String field : ProjectFields) {
                projectInfo.put(field, Constants.NA);
            }

            RequestType reqType = kickoffRequest.getRequestType();
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
                projectInfo.put(Constants.ProjectInfo.SAMPLE_TYPE, StringUtils.join(preservations, ","));

                if (!platforms.isEmpty()) {
                    projectInfo.put(Constants.ProjectInfo.PLATFORM, StringUtils.join(platforms, ","));
                } else {
                    projectInfo.put(Constants.ProjectInfo.PLATFORM, requestDataRecord.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser));
                }
                // ## Get Project Associated with it, print out necessary details
                List<DataRecord> parents = requestDataRecord.getParentsOfType(VeloxConstants.PROJECT, apiUser);
                if (parents != null && parents.size() > 0) {
                    DataRecord parentRecord = parents.get(0);

                    projectInfo.put(Constants.ProjectInfo.IGO_PROJECT_ID, requestID);
                    projectInfo.put(Constants.ProjectInfo.FINAL_PROJECT_TITLE, parentRecord.getStringVal("CMOFinalProjectTitle", apiUser));
                    projectInfo.put(Constants.ProjectInfo.CMO_PROJECT_ID, parentRecord.getStringVal("CMOProjectID", apiUser));
                    // From projct brief you have to take out the \n.
                    projectInfo.put(Constants.ProjectInfo.CMO_PROJECT_BRIEF, parentRecord.getStringVal("CMOProjectBrief", apiUser).replace("\n", "").replace("\r", ""));
                }

                projectInfo.put(Constants.ProjectInfo.PROJECT_MANAGER, requestDataRecord.getPickListVal("ProjectManager", apiUser));
                projectInfo.put(Constants.ProjectInfo.PROJECT_MANAGER_EMAIL, String.valueOf(pmEmail.get(requestDataRecord.getPickListVal("ProjectManager", apiUser))));
                projectInfo.put(Constants.ProjectInfo.README_INFO, requestDataRecord.getStringVal("ReadMe", apiUser));


                // ******************************* NEW FIELDS - will probably be moved
                // Bioinformatic Request: FASTQ and BICAnalysis (bools, request specific)
                projectInfo.put(Constants.ProjectInfo.BIOINFORMATIC_REQUEST, requestDataRecord.getPickListVal("DataDeliveryType", apiUser));

                // Data Analyst, Data Analyst E-mail
                projectInfo.put(Constants.ProjectInfo.DATA_ANALYST, requestDataRecord.getStringVal("DataAnalyst", apiUser));
                projectInfo.put(Constants.ProjectInfo.DATA_ANALYST_EMAIL, requestDataRecord.getStringVal("DataAnalystEmail", apiUser));

                // ******************************* END OF NEW FIELDS

                // Values saved because they will be used to remove duplicated emails in email child
                String LabHeadEmail = requestDataRecord.getStringVal("LabHeadEmail", apiUser).toLowerCase();
                String InvestigatorEmail = requestDataRecord.getStringVal("Investigatoremail", apiUser).toLowerCase();
                String[] LabHeadName = WordUtils.capitalizeFully(requestDataRecord.getStringVal("LaboratoryHead", apiUser)).split(" ", 2);
                String[] RequesterName = WordUtils.capitalizeFully(requestDataRecord.getStringVal("Investigator", apiUser)).split(" ", 2);

                if (LabHeadName.length > 1) {
                    projectInfo.put(Constants.ProjectInfo.LAB_HEAD, LabHeadName[1] + ", " + LabHeadName[0]);
                }
                projectInfo.put(Constants.ProjectInfo.LAB_HEAD_E_MAIL, LabHeadEmail);
                if (RequesterName.length > 1) {
                    projectInfo.put(Constants.ProjectInfo.REQUESTOR, RequesterName[1] + ", " + RequesterName[0]);
                }
                projectInfo.put(Constants.ProjectInfo.REQUESTOR_E_MAIL, InvestigatorEmail);


                // Get the Child e-mail records, if there are any
                List<DataRecord> emailList = Arrays.asList(requestDataRecord.getChildrenOfType(VeloxConstants.EMAIL, apiUser));
                if (emailList.size() > 0) {
                    List<String> allELists = new LinkedList<>();
                    for (DataRecord email : emailList) {
                        // Grab main email
                        String email_to_add = email.getStringVal(VeloxConstants.EMAIL, apiUser).toLowerCase();
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
                        projectInfo.put(Constants.ProjectInfo.ALTERNATE_EMAILS, AlternateEmails);
                    }
                }
            }

            return getTransformedProjectInfo(projectInfo);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving project info for request: %s", requestID), e);
        }

        return Collections.emptyMap();
    }

    private RequestType figureOutReqType(DataRecord request, User apiUser, DataRecordManager drm) {
        RequestType reqType = null;

        try {
            List<DataRecord> nymList = request.getDescendantsOfType(VeloxConstants.NIMBLE_GEN_HYB_PROTOCOL, apiUser);
            if (request.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser).contains("PACT") || nymList.size() != 0) {
                reqType = RequestType.IMPACT;

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
                if (requestName.contains(VeloxConstants.EXOME) || requestName.equals(org.mskcc.util.Constants.WES) || kapaList.size() != 0) {
                    reqType = RequestType.EXOME;
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


