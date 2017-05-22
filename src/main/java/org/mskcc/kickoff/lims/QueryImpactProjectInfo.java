
package org.mskcc.kickoff.lims;


import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;

import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.api.datafield.*;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxStandaloneManagerContext;
import com.velox.sapioutils.client.standalone.VeloxTask;
import com.velox.util.LogWriter;
import java.util.regex.Pattern;
import com.sampullara.cli.*; 

/**
* This class is an example standalone program.
 * 
 * @author Krista Kaz
 * 
 */
public class QueryImpactProjectInfo 
{
 private User user;
 private DataRecordManager dataRecordManager;
 private DataMgmtServer dataMgmtServer;
 private VeloxStandaloneManagerContext managerContext;
 private LogWriter logger;
 private HashSet<String> platforms = new HashSet<String>();

 public static PrintStream console = System.err;
 // Make HashMap for Project Manager emails
 private static LinkedHashMap<String, String> pmEmail = new LinkedHashMap<String, String>();
 private Map<String, String> ProjectInfoMap = new LinkedHashMap<String,String>();

 @Argument(alias = "p", description = "Project to get samples for")
 private static String project;


 public static void main(String[] args) throws ServerException {
    QueryImpactProjectInfo qe = new QueryImpactProjectInfo();


    System.setErr(new PrintStream(new OutputStream(){
        public void write(int b) {
        }
    }));

    if( args.length == 0){
      Args.usage(qe);
    }
    else{
      List<String> extra = Args.parse(qe, args);
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

   DateFormat dateFormat = new SimpleDateFormat("dd-MMM-yy");

   String filename = "Log_" + dateFormat.format(new Date()) + ".txt";
   File file = new File(logsDir, filename);
   PrintWriter printWriter = new PrintWriter(new FileWriter(file, true), true);
   try {
    LogWriter.setPrintWriter("kristakaz", printWriter);

    logger = new LogWriter(getClass());

    VeloxConnection connection = new VeloxConnection("Connection.txt");
    System.setErr(console);
    try {

     connection.openFromFile();

     if (connection.isConnected()) {
      user = connection.getUser();
      dataRecordManager = connection.getDataRecordManager();
      dataMgmtServer = connection.getDataMgmtServer();
             
     }

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
    if (printWriter != null) {
     printWriter.close();
    }
   }
  } catch (com.velox.sapioutils.client.standalone.VeloxConnectionException e ) {
            System.err.println("com.velox.sapioutils.client.standalone.VeloxConnectionException: Connection refused for all users.1 ");
            System.out.println("[ERROR] There was an issue connecting with LIMs. Someone or something else may be using this connection. Please try again in a minute or two.");
            System.exit(0);
  } catch (Throwable e) {
   logger.logError(e);
   e.printStackTrace(console);
  }
 }

public void queryProjectInfo(User apiUser, DataRecordManager drm, String requestID){
    queryProjectInfo(apiUser, drm, requestID, null);
}

public String getPI(){
    if(ProjectInfoMap.containsKey("Lab_Head_E-mail")){
        return ProjectInfoMap.get("Lab_Head_E-mail");
    }
    return "null";
}

public String getInvest(){
    if(ProjectInfoMap.containsKey("Requestor_E-mail")){
        return ProjectInfoMap.get("Requestor_E-mail");
    }
    return "null";
}

public void queryProjectInfo(User apiUser, DataRecordManager drm, String requestID, String reqType){
    // Adding PM emails to the hashmap
    pmEmail.put("Bouvier, Nancy", "bouviern@mskcc.org");
    pmEmail.put("Selcuklu, S. Duygu", "selcukls@mskcc.org");
    pmEmail.put("Bourque, Caitlin","bourquec@mskcc.org");

    // If request ID includes anything besides A-Za-z_0-9 exit
    Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
    if(! reqPattern.matcher(requestID).matches()){
        System.out.println("Malformed request ID.");
        return;
    }

    try{
     List<DataRecord> requests = drm.queryDataRecords("Request", "RequestId = '" + requestID  + "'", apiUser);
     List<String> ProjectFields = Arrays.asList("Lab_Head", "Lab_Head_E-mail", "Requestor", "Requestor_E-mail", "Platform", "Alternate_E-mails", "IGO_Project_ID", "Final_Project_Title", "CMO_Project_ID", "CMO_Project_Brief", "Project_Manager", "Readme_Info", "Data_Analyst", "Data_Analyst_E-mail"); 

     //initalizing empty map
     for(String field : ProjectFields) {
         ProjectInfoMap.put(field, "NA");
     }


     for(DataRecord request : requests){
         if(reqType == null){
             reqType = figureOutReqType(request, apiUser, drm);  
         }

         // Sample Preservation Types
         // Get samples - get their sample preservation
         List<DataRecord> Samps = Arrays.asList(request.getChildrenOfType("Sample", apiUser));
         HashSet<String> preservations = new HashSet<String>();
         if(Samps.size() > 0){
             List<Object> pres = drm.getValueList(Samps, "Preservation", apiUser);
             for(int i = 0; i < pres.size(); i++){
                 String p = String.valueOf(pres.get(i)).toUpperCase();
                 preservations.add(p);
             }
         }
         // Get Samples that are in Plates
         for(DataRecord child : request.getChildrenOfType("Plate", apiUser)){
             String ChildName = child.getName(apiUser);
             List<DataRecord> Samps2 = Arrays.asList(child.getChildrenOfType("Sample", apiUser));
             if(Samps2.size() > 0){
                 List<Object> pres = drm.getValueList(Samps, "Preservation", apiUser);
                 for(int i = 0; i < pres.size(); i++){
                     String p = String.valueOf(pres.get(i)).toUpperCase();
                     preservations.add(p);
                 }
             }
         }
         ProjectInfoMap.put("Sample_Type", StringUtils.join(preservations, ","));

         if(! platforms.isEmpty()){
             ProjectInfoMap.put("Platform", StringUtils.join(platforms, ","));
         }else{
             ProjectInfoMap.put("Platform", request.getPickListVal("RequestName", apiUser));
         }
          // ## Get Project Associated with it, print out necessary details
          List<DataRecord> parents = request.getParentsOfType("Project", apiUser);
          if(parents != null && parents.size() > 0){
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
        String[] LabHeadName = WordUtils.capitalizeFully(request.getStringVal("LaboratoryHead", apiUser)).split(" ",2);
        String[] RequesterName = WordUtils.capitalizeFully(request.getStringVal("Investigator", apiUser)).split(" ", 2);

        if(LabHeadName.length > 1){
             ProjectInfoMap.put("Lab_Head", LabHeadName[1] + ", " + LabHeadName[0]);
        }
        ProjectInfoMap.put("Lab_Head_E-mail", LabHeadEmail);
        if (RequesterName.length > 1){
            ProjectInfoMap.put("Requestor", RequesterName[1] + ", " + RequesterName[0]);
        }
        ProjectInfoMap.put("Requestor_E-mail", InvestigatorEmail);


        // Get the Child e-mail records, if there are any
        List<DataRecord> emailList = Arrays.asList(request.getChildrenOfType("Email", apiUser));
        if(emailList.size()>0){
           List<String> allELists = new LinkedList<String>();
           for(DataRecord email : emailList){
               // Grab main email
               String email_to_add=email.getStringVal("Email",apiUser).toLowerCase();
               if(!email_to_add.equals(LabHeadEmail) && 
                  !email_to_add.equals(InvestigatorEmail) && 
                  !allELists.contains(email_to_add) && email_to_add.length() != 0 ){
                   allELists.add(email_to_add);
               }
               // Grab AlternateEmail
               email_to_add=email.getStringVal("AlternateEmail", apiUser).toLowerCase();
               if(!email_to_add.equals(LabHeadEmail) && 
                  !email_to_add.equals(InvestigatorEmail) && 
                  !allELists.contains(email_to_add) && email_to_add.length() != 0 ){
                   allELists.add(email_to_add);
               }

           }
           //System.out.print("Alternate_E-mails: ");
           if(allELists.size() > 0){
               String AlternateEmails = StringUtils.join(allELists, ",");
               ProjectInfoMap.put("Alternate_E-mails", AlternateEmails);
           }
        }
      }

    printMap(ProjectInfoMap);
    
    }
    catch(NotFound nf){
        logger.logError(nf);
        nf.printStackTrace(console);
    }
    catch(IoError | RemoteException ioe){
        logger.logError(ioe);
        ioe.printStackTrace(console);
    }
    catch(Exception e){
        e.printStackTrace(console);
    }

   return ;    
    
 }

  private String figureOutReqType(DataRecord request, User apiUser, DataRecordManager drm){
      String reqType = "null";
      try{
      List<DataRecord> nymList = request.getDescendantsOfType("NimbleGenHybProtocol", apiUser);
      if(request.getPickListVal("RequestName", apiUser).toString().contains("PACT") || nymList.size() != 0){
          reqType = "impact";

	  // For each Nimb record in nymb list, get capture bait set and spike in
          if(nymList.size() > 0){
              List<Object> baitSets = drm.getValueList(nymList, "Recipe", apiUser);
              List<Object> spikeIn = drm.getValueList(nymList, "SpikeInGenes", apiUser);

              for(int i = 0; i < baitSets.size(); i++){
                  String b = String.valueOf(baitSets.get(i));
                  if(b.equals("null") || b.isEmpty()){
                      continue;
                  }
                  String s = String.valueOf(spikeIn.get(i));
                  if(s.equals("null") || s.isEmpty()){
                      platforms.add(b);
                  }else{
                      platforms.add(b + "+" + s);
                  }
              }
          }
      } else {
          List<DataRecord> kapaList = request.getDescendantsOfType("KAPAAgilentCaptureProtocol1", apiUser);
          String requestName = request.getPickListVal("RequestName", apiUser).toString();
          if(requestName.contains("Exome") || requestName.equals("WES") || kapaList.size() != 0){
              reqType = "exome";
              // For each kapa record, grab the capture type
              List<Object> baitSets = drm.getValueList(kapaList, "AgilentCaptureKit", apiUser);
              for(int i = 0; i < baitSets.size(); i++){
                  String b = String.valueOf(baitSets.get(i));
                  if(b.equals("null") || b.isEmpty()){
                      continue;
                  }
                  platforms.add(b);
              }
          }
      }
      } catch(NotFound nf){
          logger.logError(nf);
          nf.printStackTrace(console);
      } catch( RemoteException ioe){
          logger.logError(ioe);
          ioe.printStackTrace(console);
      } catch(Exception e){
          e.printStackTrace(console);
      }

      return reqType;
  }

 public void printMap(Map<String, String> hm) {
     for(String key : hm.keySet()) {
         String val = hm.get(key); 
         if(val.equals("null") || val.isEmpty()) {
             val = "NA";
         }
         System.out.println(filterToAscii(key + ": " + val));
     }
 }

 public String filterToAscii(String highUnicode){
     String lettersAdded = highUnicode.replaceAll("ß", "ss").replaceAll("æ", "ae").replaceAll("Æ", "Ae");
     return Normalizer.normalize(lettersAdded, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
 }

 //private String getTumorType(User apiUser, DataRecordManager drm, String requestID){
 
 //}

}


