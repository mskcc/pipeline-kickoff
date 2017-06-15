package org.mskcc.kickoff.lims;

import com.sampullara.cli.Args;
import com.sampullara.cli.Argument;
import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import com.velox.util.LogWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.velox.util.VeloxConstants;
import org.mskcc.kickoff.velox.util.VeloxUtils;

import java.io.*;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This class is an example standalone program.
 *
 * @author Krista Kaz
 */
class QueryImpactProjectInfo {
    private static final PrintStream console = System.err;
    // Make HashMap for Project Manager emails
    private static final LinkedHashMap<String, String> pmEmail = new LinkedHashMap<>();
    @Argument(alias = "p", description = "Project to get samples for")
    private static String project;
    private final HashSet<String> platforms = new HashSet<>();
    private final Map<String, String> ProjectInfoMap = new LinkedHashMap<>();
    private LogWriter logger;

    public static void main(String[] args) throws ServerException {
        QueryImpactProjectInfo qe = new QueryImpactProjectInfo();


        System.setErr(new PrintStream(new OutputStream() {
            public void write(int b) {
            }
        }));

        if (args.length == 0) {
            Args.usage(qe);
        } else {
            Args.parse(qe, args);
            qe.connectServer();
        }
    }

    /**
     * Connect to a server, then execute the rest of code.
     */
    private void connectServer() {
        try {
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdir();
            }

            DateFormat logDateFormat = new SimpleDateFormat("dd-MMM-yy");

            String filename = "Log_" + logDateFormat.format(new Date()) + ".txt";
            File file = new File(logsDir, filename);
            PrintWriter printWriter = new PrintWriter(new FileWriter(file, true), true);
            try {
                LogWriter.setPrintWriter("kristakaz", printWriter);

                logger = new LogWriter(getClass());

                VeloxConnection connection = VeloxUtils.getVeloxConnection("Connection.txt");
                System.setErr(console);
                try {

                    connection.open();
                    // Execute the program
                    VeloxStandalone.run(connection, new VeloxTask<Object>() {
                        @Override
                        public Object performTask() throws VeloxStandaloneException {
                            queryProjectInfo(user, dataRecordManager, project);
                            return new Object();
                        }
                    });
                } finally {
                    connection.close();
                }
            } finally {
                printWriter.close();
            }
        } catch (com.velox.sapioutils.client.standalone.VeloxConnectionException e) {
            System.err.println("com.velox.sapioutils.client.standalone.VeloxConnectionException: Connection refused for all users.1 ");
            System.out.println("[ERROR] There was an issue connecting with LIMs. Someone or something else may be using this connection. Please try again in a minute or two.");
            System.exit(0);
        } catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace(console);
        }
    }
    private void queryProjectInfo(User apiUser, DataRecordManager drm, String requestID) {
        queryProjectInfo(apiUser, drm, requestID, null);
    }

    public String getPI() {
        if (ProjectInfoMap.containsKey("Lab_Head_E-mail")) {
            return ProjectInfoMap.get("Lab_Head_E-mail");
        }
        return Constants.NULL;
    }

    public String getInvest() {
        if (ProjectInfoMap.containsKey("Requestor_E-mail")) {
            return ProjectInfoMap.get("Requestor_E-mail");
        }
        return Constants.NULL;
    }

    public void queryProjectInfo(User apiUser, DataRecordManager drm, String requestID, String reqType) {
        // Adding PM emails to the hashmap
        pmEmail.put("Bouvier, Nancy", "bouviern@mskcc.org");
        pmEmail.put("Selcuklu, S. Duygu", "selcukls@mskcc.org");
        pmEmail.put("Bourque, Caitlin", "bourquec@mskcc.org");

        // If request ID includes anything besides A-Za-z_0-9 exit
        Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
        if (!reqPattern.matcher(requestID).matches()) {
            System.out.println("Malformed request ID.");
            return;
        }

        try {
            List<DataRecord> requests = drm.queryDataRecords("Request", "RequestId = '" + requestID + "'", apiUser);
            List<String> ProjectFields = Arrays.asList("Lab_Head", "Lab_Head_E-mail", "Requestor", "Requestor_E-mail", "Platform", "Alternate_E-mails", "IGO_Project_ID", "Final_Project_Title", "CMO_Project_ID", "CMO_Project_Brief", "Project_Manager", "Readme_Info", "Data_Analyst", "Data_Analyst_E-mail");

            //initalizing empty map
            for (String field : ProjectFields) {
                ProjectInfoMap.put(field, "NA");
            }


            for (DataRecord request : requests) {
                if (reqType == null) {
                    reqType = figureOutReqType(request, apiUser, drm);
                }

                // Sample Preservation Types
                // Get samples - get their sample preservation
                List<DataRecord> Samps = Arrays.asList(request.getChildrenOfType(VeloxConstants.SAMPLE, apiUser));
                HashSet<String> preservations = new HashSet<>();
                if (Samps.size() > 0) {
                    List<Object> pres = drm.getValueList(Samps, VeloxConstants.PRESERVATION, apiUser);
                    for (Object pre : pres) {
                        String p = String.valueOf(pre).toUpperCase();
                        preservations.add(p);
                    }
                }
                // Get Samples that are in Plates
                for (DataRecord child : request.getChildrenOfType(VeloxConstants.PLATE, apiUser)) {
                    List<DataRecord> Samps2 = Arrays.asList(child.getChildrenOfType(VeloxConstants.SAMPLE, apiUser));
                    if (Samps2.size() > 0) {
                        List<Object> pres = drm.getValueList(Samps, VeloxConstants.PRESERVATION, apiUser);
                        for (Object pre : pres) {
                            String p = String.valueOf(pre).toUpperCase();
                            preservations.add(p);
                        }
                    }
                }
                ProjectInfoMap.put("Sample_Type", StringUtils.join(preservations, ","));

                if (!platforms.isEmpty()) {
                    ProjectInfoMap.put("Platform", StringUtils.join(platforms, ","));
                } else {
                    ProjectInfoMap.put("Platform", request.getPickListVal(VeloxConstants.REQUEST_NAME, apiUser));
                }
                // ## Get Project Associated with it, print out necessary details
                List<DataRecord> parents = request.getParentsOfType(VeloxConstants.PROJECT, apiUser);
                if (parents != null && parents.size() > 0) {
                    DataRecord p = parents.get(0);

                    ProjectInfoMap.put("IGO_Project_ID", requestID);
                    ProjectInfoMap.put("Final_Project_Title", p.getStringVal("CMOFinalProjectTitle", apiUser));
                    ProjectInfoMap.put("CMO_Project_ID", p.getStringVal("CMOProjectID", apiUser));
                    // From projct brief you have to take out the \n.
                    ProjectInfoMap.put("CMO_Project_Brief", p.getStringVal("CMOProjectBrief", apiUser).replace("\n", "").replace("\r", ""));
                }

                ProjectInfoMap.put("Project_Manager", request.getPickListVal("ProjectManager", apiUser));
                ProjectInfoMap.put("Project_Manager_Email", String.valueOf(pmEmail.get(request.getPickListVal("ProjectManager", apiUser))));
                ProjectInfoMap.put("Readme_Info", request.getStringVal("ReadMe", apiUser));


                // ******************************* NEW FIELDS - will probably be moved
                // Bioinformatic Request: FASTQ and BICAnalysis (bools, request specific)
                ProjectInfoMap.put("Bioinformatic_Request", request.getPickListVal("DataDeliveryType", apiUser));

                // Data Analyst, Data Analyst E-mail
                ProjectInfoMap.put("Data_Analyst", request.getStringVal("DataAnalyst", apiUser));
                ProjectInfoMap.put("Data_Analyst_E-mail", request.getStringVal("DataAnalystEmail", apiUser));

                // ******************************* END OF NEW FIELDS

                // Values saved because they will be used to remove duplicated emails in email child
                String LabHeadEmail = request.getStringVal("LabHeadEmail", apiUser).toLowerCase();
                String InvestigatorEmail = request.getStringVal("Investigatoremail", apiUser).toLowerCase();
                String[] LabHeadName = WordUtils.capitalizeFully(request.getStringVal("LaboratoryHead", apiUser)).split(" ", 2);
                String[] RequesterName = WordUtils.capitalizeFully(request.getStringVal("Investigator", apiUser)).split(" ", 2);

                if (LabHeadName.length > 1) {
                    ProjectInfoMap.put("Lab_Head", LabHeadName[1] + ", " + LabHeadName[0]);
                }
                ProjectInfoMap.put("Lab_Head_E-mail", LabHeadEmail);
                if (RequesterName.length > 1) {
                    ProjectInfoMap.put("Requestor", RequesterName[1] + ", " + RequesterName[0]);
                }
                ProjectInfoMap.put("Requestor_E-mail", InvestigatorEmail);


                // Get the Child e-mail records, if there are any
                List<DataRecord> emailList = Arrays.asList(request.getChildrenOfType("Email", apiUser));
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
                        ProjectInfoMap.put("Alternate_E-mails", AlternateEmails);
                    }
                }
            }

            printMap(ProjectInfoMap);

        } catch (NotFound | IoError | RemoteException nf) {
            logger.logError(nf);
            nf.printStackTrace(console);
        } catch (Exception e) {
            e.printStackTrace(console);
        }
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
        } catch (NotFound | RemoteException nf) {
            logger.logError(nf);
            nf.printStackTrace(console);
        } catch (Exception e) {
            e.printStackTrace(console);
        }

        return reqType;
    }

    private void printMap(Map<String, String> hm) {
        for (String key : hm.keySet()) {
            String val = hm.get(key);
            if (val.equals(Constants.NULL) || val.isEmpty()) {
                val = "NA";
            }
            System.out.println(filterToAscii(key + ": " + val));
        }
    }

    private String filterToAscii(String highUnicode) {
        String lettersAdded = highUnicode.replaceAll("ß", "ss").replaceAll("æ", "ae").replaceAll("Æ", "Ae");
        return Normalizer.normalize(lettersAdded, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }
}


