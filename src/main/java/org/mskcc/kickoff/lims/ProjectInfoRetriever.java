package org.mskcc.kickoff.lims;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.sqlbuilder.Column;
import com.velox.api.sqlbuilder.SqlBuilder;
import com.velox.api.sqlbuilder.Table;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.log4j.Logger;
import org.mskcc.domain.RequestType;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.util.VeloxConstants;
import org.springframework.stereotype.Component;

import java.rmi.RemoteException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This class is an example standalone program.
 *
 * @author Krista Kaz
 */
@Component
public class ProjectInfoRetriever {
    private static final Logger DEV_LOGGER = Logger.getLogger(org.mskcc.kickoff.util.Constants.DEV_LOGGER);

    @Argument(alias = "p", description = "Project to get samples for")
    private final HashSet<String> platforms = new HashSet<>();
    private Map<String, String> projectInfo = new LinkedHashMap<>();
    private List<String> projectFields = Arrays.asList(
            Constants.ProjectInfo.LAB_HEAD,
            Constants.ProjectInfo.LAB_HEAD_E_MAIL,
            Constants.ProjectInfo.REQUESTOR,
            Constants.ProjectInfo.REQUESTOR_E_MAIL,
            Constants.ProjectInfo.PLATFORM,
            Constants.ProjectInfo.IGO_PROJECT_ID,
            Constants.ProjectInfo.FINAL_PROJECT_TITLE,
            Constants.ProjectInfo.CMO_PROJECT_ID,
            Constants.ProjectInfo.CMO_PROJECT_BRIEF,
            Constants.ProjectInfo.PROJECT_MANAGER,
            Constants.ProjectInfo.README_INFO,
            Constants.ProjectInfo.DATA_ANALYST,
            Constants.ProjectInfo.DATA_ANALYST_EMAIL,
            Constants.ProjectInfo.PI_EMAIL,
            Constants.ProjectInfo.PI_FIRSTNAME,
            Constants.ProjectInfo.PI_LASTNAME,
            Constants.ProjectInfo.CONTACT_NAME,
            Constants.ProjectInfo.PROJECT_APPLICATIONS);

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

    public Map<String, String> queryProjectInfo(User apiUser, DataRecordManager drm, KickoffRequest kickoffRequest) {

        // If request ID includes anything besides A-Za-z_0-9 exit
        Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
        String requestID = kickoffRequest.getId();
        if (!reqPattern.matcher(requestID).matches()) {
            DEV_LOGGER.error(String.format("Malformed request ID: <%s>.", requestID));
            return Collections.emptyMap();
        }

        try {
            List<DataRecord> requests = drm.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" + requestID +
                    "'", apiUser);

            //initalizing empty map
            for (String field : projectFields) {
                projectInfo.put(field, Constants.NA);
            }

            RequestType reqType = kickoffRequest.getRequestType();
            for (DataRecord requestDataRecord : requests) {
                if (reqType == null) {
                    reqType = figureOutReqType(requestDataRecord, apiUser, drm);
                }

                // Sample Preservation Types
                // Get samples - get their sample preservation
                List<DataRecord> Samps = Arrays.asList(requestDataRecord.getChildrenOfType(VeloxConstants.SAMPLE,
                        apiUser));
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
                    projectInfo.put(Constants.ProjectInfo.PLATFORM, requestDataRecord.getPickListVal(VeloxConstants
                            .REQUEST_NAME, apiUser));
                }
                // ## Get Project Associated with it, print out necessary details
                List<DataRecord> parents = requestDataRecord.getParentsOfType(VeloxConstants.PROJECT, apiUser);
                if (parents != null && parents.size() > 0) {
                    DataRecord parentRecord = parents.get(0);

                    projectInfo.put(Constants.ProjectInfo.IGO_PROJECT_ID, requestID);
                    projectInfo.put(Constants.ProjectInfo.FINAL_PROJECT_TITLE, parentRecord.getStringVal
                            ("CMOFinalProjectTitle", apiUser));
                    projectInfo.put(Constants.ProjectInfo.CMO_PROJECT_ID, parentRecord.getStringVal("CMOProjectID",
                            apiUser));
                    // From projct brief you have to take out the \n.
                    projectInfo.put(Constants.ProjectInfo.CMO_PROJECT_BRIEF, parentRecord.getStringVal
                            ("CMOProjectBrief", apiUser).replace("\n", "").replace("\r", ""));
                }

                String projectManager = requestDataRecord.getPickListVal("ProjectManager", apiUser);
                String projectManagerEmail = getProjectManagerEmail(drm, apiUser, projectManager);
                projectInfo.put(Constants.ProjectInfo.PROJECT_MANAGER, projectManager);
                projectInfo.put(Constants.ProjectInfo.PROJECT_MANAGER_EMAIL, projectManagerEmail.trim());
                projectInfo.put(Constants.ProjectInfo.README_INFO,
                        requestDataRecord.getStringVal("ReadMe", apiUser));

                // Bioinformatic Request: FASTQ and BICAnalysis (bools, request specific)
                projectInfo.put(Constants.ProjectInfo.BIOINFORMATIC_REQUEST, requestDataRecord.getPickListVal
                        ("DataDeliveryType", apiUser));

                // Data Analyst, Data Analyst E-mail
                projectInfo.put(Constants.ProjectInfo.DATA_ANALYST, requestDataRecord.getStringVal("DataAnalyst",
                        apiUser));
                projectInfo.put(Constants.ProjectInfo.DATA_ANALYST_EMAIL, requestDataRecord.getStringVal
                        ("DataAnalystEmail", apiUser));

                projectInfo.put(Constants.ProjectInfo.PROJECT_APPLICATIONS, requestDataRecord.getStringVal
                        ("PlatformApplication", apiUser));

                setUpPIandInvest(requestDataRecord, apiUser, projectInfo);
            }

            return getTransformedProjectInfo(projectInfo);
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while retrieving project info for request: %s",
                    requestID), e);
        }

        return Collections.emptyMap();
    }

    private void setUpPIandInvest(DataRecord request, User apiUser, Map<String, String> projectInfo) throws Exception {
        // Values saved because they will be used to remove duplicated emails in email child
        String labHeadEmail = request.getStringVal("LabHeadEmail", apiUser).toLowerCase();
        String investigatorEmail = request.getStringVal("Investigatoremail", apiUser).toLowerCase();
        String labHead = request.getStringVal("LaboratoryHead", apiUser);
        String[] requesterName = WordUtils.capitalizeFully(request.getStringVal("Investigator", apiUser)).split(" ", 2);

        projectInfo.put(Constants.ProjectInfo.LAB_HEAD, labHead);
        projectInfo.put(Constants.ProjectInfo.LAB_HEAD_E_MAIL, labHeadEmail);
        if (requesterName.length > 1) {
            projectInfo.put(Constants.ProjectInfo.REQUESTOR, requesterName[1] + ", " + requesterName[0]);
        }
        projectInfo.put(Constants.ProjectInfo.INVESTIGATOR_EMAIL, investigatorEmail);

        String piemail = request.getStringVal("PIemail", apiUser);
        String piFirstName = request.getStringVal("PIFirstName", apiUser);
        String piLastName = request.getStringVal("PILastName", apiUser);
        // contactname is storing cmo email
        String contactName = request.getStringVal("ContactName", apiUser);
        if (StringUtils.isNotBlank(piemail)) {
            projectInfo.put(Constants.ProjectInfo.PI_EMAIL, piemail.trim().toLowerCase());
        }

        if (StringUtils.isNotBlank(piFirstName)) {
            projectInfo.put(Constants.ProjectInfo.PI_FIRSTNAME, WordUtils.capitalize(piFirstName.trim()));
        }

        if (StringUtils.isNotBlank(piLastName)) {
            projectInfo.put(Constants.ProjectInfo.PI_LASTNAME, WordUtils.capitalize(piLastName.trim()));
        }

        if (StringUtils.isNotBlank(contactName)) {
            projectInfo.put(Constants.ProjectInfo.CONTACT_NAME, contactName.trim().toLowerCase());
        }
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
                List<DataRecord> kapaList = request.getDescendantsOfType(VeloxConstants
                        .KAPA_AGILENT_CAPTURE_PROTOCOL_1, apiUser);
                String requestName = request.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser);
                if (requestName.contains(VeloxConstants.EXOME) || requestName.equals(org.mskcc.util.Constants.WES) ||
                        kapaList.size() != 0) {
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

    String getProjectManagerEmail(DataRecordManager dataRecordManager, User apiUser, String projectManagerFullName) {
        String firstName = "";
        String lastName = "";

        String[] temp = projectManagerFullName
                .replaceAll(",", "")
                .replace(".", "")
                .split("\\s+");

        if (temp.length == 1) {
            DEV_LOGGER.warn(String.format("Not valid full name: <%s>.", projectManagerFullName));
        } else if (temp.length == 2) {
            firstName = temp[1].trim();
            lastName = temp[0].trim();
        } else if (temp.length == 3) {
            firstName = temp[2].trim();
            lastName = temp[0].trim();
        } else {
            DEV_LOGGER.warn(String.format("Not valid full name: <%s>.", projectManagerFullName));
        }

        Optional<String> optionalPmEmail = Optional.empty();
        try {
            optionalPmEmail = queryDatabaseForProjectManagerEmail(dataRecordManager, apiUser, firstName, lastName);
        } catch (Exception e) {
            DEV_LOGGER.warn(e.getMessage());
        }
        return optionalPmEmail.orElse(Constants.NA);
    }

    Optional<String> queryDatabaseForProjectManagerEmail(DataRecordManager dataRecordManager, User apiUser, String
            firstName, String lastName) throws ServerException, RemoteException {
        SqlBuilder sqlBuilder = new SqlBuilder();
        Table limsuserTable = new Table("limsuser");
        Column emailaddressColumn = new Column(limsuserTable, "EMAILADDRESS");
        Column firstnameColumn = new Column(limsuserTable, "FIRSTNAME");
        Column lastnameColumn = new Column(limsuserTable, "LASTNAME");
        sqlBuilder.select(emailaddressColumn)
                .from(limsuserTable)
                .where(firstnameColumn.equalTo(firstName)
                        .and(lastnameColumn.equalTo(lastName)));

        List<Map<Column, Object>> limsUsers = dataRecordManager.queryDatabase(sqlBuilder, apiUser);

        if (limsUsers.size() >= 1) {
            return Optional.of((String) limsUsers.get(0).get(emailaddressColumn));
        } else {
            DEV_LOGGER.warn(String.format("<%d> lims user found for <%s %s>.", limsUsers.size(), firstName, lastName));
        }
        return Optional.empty();
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
