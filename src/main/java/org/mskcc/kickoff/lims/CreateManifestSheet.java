
package org.mskcc.kickoff.lims;

//Not sure if I need ALL these for excel, but it worked when I put them in.
import org.apache.commons.lang3.text.WordUtils;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.openxml4j.exceptions.*;
import org.apache.poi.openxml4j.opc.*;

//These were already here or added by me as needed 
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.logging.*;
import java.util.regex.Pattern;
import static java.nio.file.StandardCopyOption.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils; 
import com.velox.api.datamgmtserver.DataMgmtServer;
import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxStandaloneManagerContext;
import com.velox.sapioutils.client.standalone.VeloxTask;
import com.velox.util.LogWriter;
import java.lang.Thread;

import com.sampullara.cli.*;
/**
 * This class is an example standalone program.
 *
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 *
 *
 */
public class CreateManifestSheet
{
    private final String productionProjectFilePath = "/ifs/projects/BIC";
    private final String draftProjectFilePath = "/ifs/projects/BIC/drafts";
    private final String requestTypeMappingFile = "all_impact_rnaseq_mapping_LIMS_names.txt";
    private final String archivePath = "/home/reza/testIfs/projects/BIC/archive";
    private final String fastq_path = "/ifs/archive/GCL";
    private final String emailTo = "kristakaz@cbio.mskcc.org";
    private final String emailFrom = "bic-request@cbio.mskcc.org";
    private final String emailHost = "cbio.mskcc.org"; // putting this because that is what Aaron has in his validator
    private final String designFilePath= "/ifs/projects/CMO/targets/designs";
    private final String resultsPathPrefix = "/ifs/solres/seq";
    private final String humanAbrevSpecies = "b37";
    private final String mouseAbrevSpecies = "mm10";    
    private final String sampleKeyExFile = "SampleKeyExamples.txt";


    private String extraReadmeInfo = "";
    private String requestSpecies = "#EMPTY";
    private List<String> xenograftClasses = Arrays.asList("PDX", "Xenograft", "XenograftDerivedCellLine");
    private String manualMappingPinfoToRequestFile = "Alternate_E-mails:DeliverTo,Lab_Head:PI_Name,Lab_Head_E-mail:PI,Requestor:Investigator_Name,Requestor_E-mail:Investigator,CMO_Project_ID:ProjectName,Final_Project_Title:ProjectTitle,CMO_Project_Brief:ProjectDesc";
    private String manualMappingPatientHeader = "Pool:REQ_ID,Sample_ID:CMO_SAMPLE_ID,Collab_ID:INVESTIGATOR_SAMPLE_ID,Patient_ID:CMO_PATIENT_ID,Class:SAMPLE_CLASS,Sample_type:SPECIMEN_PRESERVATION_TYPE,Input_ng:LIBRARY_INPUT,Library_yield:LIBRARY_YIELD,Pool_input:CAPTURE_INPUT,Bait_version:BAIT_VERSION,Sex:SEX";
    private String manualClinicalHeader = "SAMPLE_ID:CMO_SAMPLE_ID,PATIENT_ID:CMO_PATIENT_ID,COLLAB_ID:INVESTIGATOR_SAMPLE_ID,SAMPLE_TYPE:SAMPLE_TYPE,GENE_PANEL:BAIT_VERSION,ONCOTREE_CODE:ONCOTREE_CODE,SAMPLE_CLASS:SAMPLE_CLASS,SPECIMEN_PRESERVATION_TYPE:SPECIMEN_PRESERVATION_TYPE,SEX:SEX,TISSUE_SITE:TISSUE_SITE";
    private List<String> hashMapHeader = Arrays.asList("MANIFEST_SAMPLE_ID", "CMO_PATIENT_ID", "INVESTIGATOR_SAMPLE_ID", "INVESTIGATOR_PATIENT_ID", "ONCOTREE_CODE", "SAMPLE_CLASS", "TISSUE_SITE", "SAMPLE_TYPE", "SPECIMEN_PRESERVATION_TYPE", "SPECIMEN_COLLECTION_YEAR", "SEX", "BARCODE_ID", "BARCODE_INDEX", "LIBRARY_INPUT", "LIBRARY_YIELD", "CAPTURE_INPUT", "CAPTURE_NAME", "CAPTURE_CONCENTRATION", "CAPTURE_BAIT_SET", "SPIKE_IN_GENES", "STATUS", "INCLUDE_RUN_ID", "EXCLUDE_RUN_ID");
    private String manualMappingHashMap =  "LIBRARY_INPUT:LIBRARY_INPUT[ng],LIBRARY_YIELD:LIBRARY_YIELD[ng],CAPTURE_INPUT:CAPTURE_INPUT[ng],CAPTURE_CONCENTRATION:CAPTURE_CONCENTRATION[nM],MANIFEST_SAMPLE_ID:CMO_SAMPLE_ID";
    private List<String> portalConfigFields = Arrays.asList("cna_seg","cna_seg_desc","cna","maf","maf_desc","name","invest","invest_name","project","tumor_type","inst","date_of_last_update","groups","data_clinical","assay_type","desc");
    private String manualMappingConfigMap = "name:ProjectTitle,desc:ProjectDesc,invest:PI,invest_name:PI_Name,tumor_type:TumorType,date_of_last_update:DateOfLastUpdate,assay_type:Assay";
    private List<String> sampleSwapValues = Arrays.asList("IGO_ID", "INVESTIGATOR_PATIENT_ID", "INVESTIGATOR_SAMPLE_ID", "SAMPLE_CLASS", "SAMPLE_TYPE", "SPECIMEN_COLLECTION_YEAR", "SPECIMEN_PRESERVATION_TYPE", "SPECIES", "ONCOTREE_CODE", "TISSUE_SITE", "SEX", "CMO_SAMPLE_ID", "CMO_PATIENT_ID");
    private Boolean force = false;
    private Boolean mappingIssue = false;
    private static int NewMappingScheme = 0;
    private static HashSet<String> runIDlist = new HashSet<String>();
    private int runNum;
    private String baitVersion = "#EMPTY";
    private String pi;
    private String invest;
    private User user;
    private HashSet<File> filesCreated = new HashSet<File>();
    private DataRecordManager dataRecordManager;
    private DataMgmtServer dataMgmtServer;
    private DecimalFormat four = new DecimalFormat("#0.0000");
    private VeloxStandaloneManagerContext managerContext;
    private Map<String, String> RunID_and_PoolName = new LinkedHashMap<String, String>();
    private LogWriter deadLog;
    private Logger logger = Logger.getLogger(CreateManifestSheet.class.getName());
    private FileHandler log_fh = null;
    private File outLogDir = null;
    private String ReqType = "";
    private String recipe = "";
    private ArrayList<DataRecord> passingSeqRuns = new ArrayList<DataRecord>();
    private Boolean exitLater = false;
    public static PrintStream console = System.err;
    private static VeloxConnection connection;
    private LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput = new LinkedHashMap<String, LinkedHashMap<String, String>>();


    private String poolQCWarnings = "";
    private Set<String> sampleRuns = new HashSet<String>();
    private Map<String, Set<String>> badRuns = new HashMap<String, Set<String>>(); 
    private Set<String> poolRuns = new HashSet<String>();
    private ArrayList<String> log_messages = new ArrayList<String>();
    private Map<String, Integer> readsForSample = new HashMap<String, Integer>();
    private Map<String, String> manualOverrides = new HashMap<String, String>();

    //This collects all library protocol types and Amplification protocol types
    private Set<String> libType = new HashSet<String>();
    private Set<String> ampType = new HashSet<String>();
    private Set<String> strand = new HashSet<String>();

    // This is for sample renames and sample swaps
    private HashMap<String,String> sampleRenames = new HashMap<String,String>();
    private HashMap<String,String> sampleSwap = new HashMap<String,String>();
    private HashMap<String,String> sampleAlias = new HashMap<String,String>();
 
    //This is exception list, these columns are OKAY if they are empty, (gets na) rather than #empty 
    private List <String> exceptionList = Arrays.asList("TISSUE_SITE", "SPECIMEN_COLLECTION_YEAR","SPIKE_IN_GENES");
    private List <String> silentList = Arrays.asList("STATUS", "EXCLUDE_RUN_ID"); 
 

    @Argument(alias = "p", description = "Project to get samples for", required = true)
    private static String project;
    
    @Argument(alias = "t", description = "Testing projects")
    private static Boolean test = false;

    @Argument(alias = "k", description = "Krista's argument. For her testing")
    private static Boolean krista = false;    

    @Argument(alias = "prod", description = "Production project files (goes in specific path) default to draft directory")
    private static Boolean prod = false;

    @Argument(alias = "noPortal", description = "This is suppress creation of portal config file.")
    private static Boolean noPortal = false;

    @Argument(alias = "f", description = "Force pulling all samples even if they don't have QC passed.")
    private static Boolean forced = false;

    @Argument(alias = "exome", description = "Run exome project even IF project pulls as an impact")
    private static Boolean runAsExome = false;

    @Argument(alias = "s", description = "Shiny user is running this script (rnaseq projects will die).")
    private static Boolean shiny = false;

    @Argument(alias = "o", description = "Pipeline files output dir")
    private static String outdir;    

    @Argument(alias = "rerunReason", description = "Reason for rerun, *REQUIRED if this is not the first run for this project*")
    private static String rerunReason;

    @Argument(alias = "options", description = "Pipeline files output dir")
    private static String[] pipeline_options;

    private static Boolean draft = false;

    Set<PosixFilePermission> DIRperms = new HashSet<PosixFilePermission>();
    Set<PosixFilePermission> FILEperms = new HashSet<PosixFilePermission>();
 
    public static void main(String[] args) throws ServerException {

        CreateManifestSheet qe = new CreateManifestSheet();

        if( args.length == 0){
            Args.usage(qe);
        }
        else{
            try{
                List<String> extra = Args.parse(qe, args);
            } catch(IllegalArgumentException e ){
                Args.usage(qe);
            }

            // Shut down hook so that program exits correctly
            MySafeShutdown sh = new MySafeShutdown();
            Runtime.getRuntime().addShutdownHook(sh);

            qe.connectServer();

        }
    }

    private static void closeConnection(){
        if(connection.isConnected()){
            try{ 
                connection.close();
            }  catch (Throwable e) {
            e.printStackTrace(console);
            }
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
            
            System.setErr(new PrintStream(new OutputStream(){
                public void write(int b) {
                }
            }));
            
            DateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");
            String filename = "Log_" + dateFormat.format(new Date()) + ".txt";

            if(shiny){
                filename = "Log_" + dateFormat.format(new Date()) + "_shiny.txt";
            }
            File file = new File(logsDir + "/" + filename);
            if( !file.exists()){
               file.createNewFile();
               file.setWritable(true,false);
               file.setReadable(true,false);
            }
            //PrintWriter printWriter = new PrintWriter(new FileWriter(file, true), true);
            log_fh = new FileHandler(logsDir + "/" + filename, true);
            log_fh.setFormatter(new SimpleFormatter());
            logger.addHandler(log_fh);
            logger.setUseParentHandlers(false);

           // If request ID includes anything besides A-Za-z_0-9 exit
           // This is done up here so we don't have to connect, then try to query a malformed project id, which could mess up the system.
           Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
           if(! reqPattern.matcher(project).matches()){
               print("Malformed request ID.");
               return;
            }

            try {
                //LogWriter.setPrintWriter("kristakaz", printWriter);
                connection = new VeloxConnection("Connection.txt");
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
                            try{
                                queryProjectInfo(user, dataRecordManager, project);
                            } catch ( IOException e ) {
                            } 
                            return new Object();
                        }
                    });
                } finally {
                    closeConnection();
                }
            } finally {
                if (log_fh != null) {
                    log_fh.close();
                }
            }
        } catch (com.velox.sapioutils.client.standalone.VeloxConnectionException e ) {
            System.err.println("com.velox.sapioutils.client.standalone.VeloxConnectionException: Connection refused for all users.1 ");
            e.printStackTrace(console);
            //print("[ERROR] There was an issue connecting with LIMs. Someone or something else may be using this connection. Please try again in a minute or two.");
            System.exit(0); 
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
    }
   
    // Main script.  
    private void queryProjectInfo(User apiUser, DataRecordManager drm, String requestID) throws IOException{
        initializeFilePermissions();
        // If request ID includes anything besides A-Za-z_0-9 exit
        Pattern reqPattern = Pattern.compile("^[0-9]{5,}[A-Z_]*$");
        if(! reqPattern.matcher(requestID).matches()){
            print("Malformed request ID.");
            return;
        }
        // this is to set up if production or draft (depreciated, everything goes to the draft project file path)
        draft = !prod;
        // Sets up project file path
        String projectFilePath = productionProjectFilePath + "/" + ReqType + "/Proj_" + requestID;
        if(draft){
            projectFilePath = draftProjectFilePath + "/Proj_" + requestID;
        }

        try{
            List<DataRecord> requests = drm.queryDataRecords("Request", "RequestId = '" + requestID  + "'", apiUser);
            
            if (requests.size() == 0) {
                print("[INFO] No matching request id.");
                return;
            }
 
            //checks argument outdir. If someone put this, it creates a directory for this project, and changes project file path to outdir 
            if(outdir != null && !outdir.isEmpty()){
                File f = new File(outdir);
                if(f.exists() && f.isDirectory()) {
                    outdir += "/Proj_" + requestID;
                    File i = new File(outdir);
                    if( ! i.exists()){
                        i.mkdir();
                    }
                    print("[INFO] Overwriting default dir to " + outdir);
                    projectFilePath = outdir;
                } else {
                    print("[ERROR] The outdir directory you gave me is empty or does not exist: " + outdir);
                }
            }
            // create directory, create log dir
            File projDir = new File(projectFilePath);
            if (!projDir.exists() ) {
                projDir.mkdir();
            }
            outLogDir = new File(projectFilePath + "/logs");
            if (!outLogDir.exists()){
                outLogDir.mkdir();
            }
    
            for(DataRecord request : requests){
                // First check to see if I can autogen the files for this project:
                boolean manualDemux = false;
                boolean autoGenAble = false;

                try{
                    autoGenAble = request.getBooleanVal("BicAutorunnable", apiUser);
                    manualDemux = request.getBooleanVal("ManualDemux", apiUser);
                } catch(NullPointerException e){
                }
                if(! autoGenAble){
                    // get reason why if there is one. Found in readme of request with field "NOT_AUTORUNNABLE"
                    String reason = "";
                    String[] bicReadmeLines = (request.getStringVal("ReadMe", apiUser)).split("\\n\\r|\\n");
                    for (String bicLine : bicReadmeLines){
                        if( bicLine.startsWith("NOT_AUTORUNNABLE")){
                            reason = "REASON: " + bicLine;
                            break;
                        }
                    }
                    print("[ERROR] According to the LIMS, project " + requestID + " cannot be run through this script. " + reason + " Sorry :(");
                    if(! krista){
                        return;
                    }
                }

                // Maps that link samples, pools, and run IDs
                Map<String, Set<String>> SamplesAndRuns = new LinkedHashMap<String, Set<String>>();
                Set<String> RunSet = new HashSet<String>();

                //Pull sample specific QC for this Request:
                Map<String, Set<String>> TempSamplesAndRuns = getSampSpecificQC(requestID, drm, user);
                SamplesAndRuns = getPoolSpecificQC(request, drm, user);

                Map<String, Set<String>> pool_samp_comparison = compareSamplesAndRuns(SamplesAndRuns, poolRuns, TempSamplesAndRuns, sampleRuns, requestID, manualDemux);

                SamplesAndRuns.clear();
                //SamplesAndRuns.putAll(pool_samp_comparison);

                SamplesAndRuns = checkPostSeqQC(apiUser, drm, request, pool_samp_comparison); 
 
                if ( SamplesAndRuns.size() == 0) {
                    if (!forced && !manualDemux) {
                        System.err.println("[ERROR] No sequencing runs found for this Request ID.");
                        return;
                    } else if(manualDemux){
                        print("[WARNING] MANUAL DEMULTIPLEXING was performed. Nothing but the request file should be output.");
                    } else {
                        print("[WARNING] ALERT: There are no sequencing runs passing QC for this run. Force is true, I will pull ALL samples for this project.");
                        force = true;
                    }
                }

                // Here, check to make sure all of the samples that passed have good read counts
                checkReadCounts(); 

                // get libType, also set ReqType if possible
                getLibTypes(passingSeqRuns, drm, apiUser, request);

                if (ReqType.isEmpty()){
                    // Here I will pull the childs field recipe
                    recipe = getRecipe(drm,apiUser,request);
                    print("[WARNING] RECIPE: " + recipe);
                    if(request.getPickListVal("RequestName", apiUser).matches("(.*)PACT(.*)")) {
                        ReqType = "impact";
                    }

                    if(recipe.equals("SMARTerAmpSeq")){
                        libType.add("SMARTer Amplification");
                        strand.add("None");
                        ReqType = "rnaseq";

                    }
                    if(ReqType.length() == 0) {
                        if(requestID.startsWith("05500")){
                            print("[WARNING] 05500 project. This should be pulled as an impact.");
                            ReqType = "impact";
                        }else{
                            print("[WARNING] Request Name doesn't match one of the supported request types: " + request.getPickListVal("RequestName", apiUser) + ". Information will be pulled as if it is an rnaseq/unknown run.");
                            ReqType = "other";
                        }
                    }
                }
                
                if(shiny && ReqType.equals("rnaseq")){
                    System.err.println("[ERROR] This is an RNASeq project, and you cannot grab this information yet via Shiny");
                    return;
                }

                // Get Samples that are supposed to be output, put them in this list!
                    
                for(DataRecord child : request.getChildrenOfType("Plate", apiUser)){
                    String ChildName = child.getName(apiUser);
                    for(DataRecord wells : child.getChildrenOfType("Sample", apiUser)) {
                        String wellStat= wells.getSelectionVal("ExemplarSampleStatus", apiUser);
                        if(wellStat.length() > 0){
                            String sid = wells.getStringVal("OtherSampleId", apiUser);
                            // Check to see if sample name is used already!
                            if(SampleListToOutput.containsKey(sid)){
                                System.err.println("[ERROR] This request has two samples that have the same name: " + sid);
                                return;
                            }
                            if((SamplesAndRuns.containsKey(sid)) || (force)){
                                if (wells.getParentsOfType("Sample", apiUser).size() > 0){
                                    print("This sample is a tranfer from another request!");
                                    LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, wells, SamplesAndRuns, RunID_and_PoolName, false, true);
                                    tempHashMap.put("REQ_ID", "Proj_" + requestID);
                                    SampleListToOutput.put(sid, tempHashMap);
                                } else {
                                    LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, wells, SamplesAndRuns, RunID_and_PoolName, false, false);
                                    tempHashMap.put("REQ_ID", "Proj_" + requestID);
                                    SampleListToOutput.put(sid, tempHashMap);
                                }
                            }
                        }//if well status is not empty
                    } //end of platewell for loop
                }// end of plate loop
                    
                for(DataRecord child : request.getChildrenOfType("Sample", apiUser)){
                    // Is this sample sequenced?
                    String sid = child.getStringVal("OtherSampleId", apiUser);

                    // Added because we were getting samples that had the same name as a sequenced sample, but then it was failed so it shouldn't be used (as per Aaron).
                    String status = child.getSelectionVal("ExemplarSampleStatus", apiUser);
                    if(status.equals("Failed - Completed")){
                        print("Skipping " + sid + " because the sample is failed: " + status );
                        continue;
                    }

                    // This is never supposed to happen.
                    if(SampleListToOutput.containsKey(sid) ){
                        print("[ERROR] This request has two samples that have the same name: " + sid);
                    }
                    // if this sample is in the list of 
                    if((SamplesAndRuns.containsKey(sid)) || (force) ){
                        if (child.getParentsOfType("Sample", apiUser).size() > 0){
                            print("This sample is a tranfer from another request!");
                            LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, child, SamplesAndRuns, RunID_and_PoolName, false, true);
                            tempHashMap.put("REQ_ID", "Proj_" +  requestID);
                            SampleListToOutput.put(sid, tempHashMap);   
                        } else {
                            LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, child, SamplesAndRuns, RunID_and_PoolName, false, false);
                            tempHashMap.put("REQ_ID", "Proj_" +  requestID);
                            SampleListToOutput.put(sid, tempHashMap);
                        }

                        //return;  // FOR TESTING PURPOSES ( printTree)
                    }
                }// end of sample loop

                if(ReqType == "impact" && runAsExome){
                    ReqType="exome";
                }
 
                int numSamples = SampleListToOutput.size();
                if (numSamples == 0){
                    print("[ERROR] None of the samples in the project were found in the passing samples and runs. Please check the LIMs to see if the names are incorrect.");
                }

                // POOLED NORMAL START
                HashMap<DataRecord,HashSet<String>> PooledNormalSamples = SampleInfoImpact.getPooledNormals();
                if(PooledNormalSamples != null && PooledNormalSamples.size() > 0){
                    print("Number of Pooled Normal Samples: " + PooledNormalSamples.size());

                    // for each pooled normal, get the run ID from the pool, then add the sample and runID to that one variable.
                    SamplesAndRuns = addPooledNormalstoSampleList(SamplesAndRuns, RunID_and_PoolName, PooledNormalSamples, apiUser);

                    HashSet<DataRecord> keyCopy = new HashSet<DataRecord>(PooledNormalSamples.keySet());

                    // Here go through the control samples, and get the info needed
                    for (DataRecord PNorms : keyCopy) {
                        String pNorm_name = PNorms.getStringVal("OtherSampleId", apiUser);
                        LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, PNorms, SamplesAndRuns, RunID_and_PoolName, true, false);
                        tempHashMap.put("REQ_ID", "Proj_" +  requestID);

                        // If include run ID is 'null' skip.
                        // This could mess up some older projects, so I may have to change this
                        if(tempHashMap.get("INCLUDE_RUN_ID") == null){
                            print("[WARNING] Skipping adding pooled normal info from " + tempHashMap.get("IGO_ID") + " because I cannot find include run id. " );
                            continue;
                        }

                        // If the sample pooled normal type (ex: FROZEN POOLED NORMAL) is already in the manfiest list
                        // Concatenate the include/ exclude run ids
                        if(SampleListToOutput.containsKey(pNorm_name) && tempHashMap.get("INCLUDE_RUN_ID") != null ){
        
                            print("Combining Two Pooled Normals: " + pNorm_name);
                            LinkedHashMap<String, String> originalPooledNormalSample = SampleListToOutput.get(pNorm_name);
                            Set<String> currIncludeRuns = new HashSet<String>(Arrays.asList(originalPooledNormalSample.get("INCLUDE_RUN_ID").split(";")));
                            print("OLD include runs: " + originalPooledNormalSample.get("INCLUDE_RUN_ID") ); //StringUtils.join(currIncludeRuns, ";"));
                            Set<String> currExcludeRuns = new HashSet<String>(Arrays.asList(originalPooledNormalSample.get("EXCLUDE_RUN_ID").split(";")));

                            currIncludeRuns.addAll(Arrays.asList(tempHashMap.get("INCLUDE_RUN_ID").split(";")));
                            currExcludeRuns.addAll(Arrays.asList(originalPooledNormalSample.get("EXCLUDE_RUN_ID").split(";")));

                            tempHashMap.put("INCLUDE_RUN_ID", StringUtils.join(currIncludeRuns, ";"));
                            tempHashMap.put("EXCLUDE_RUN_ID", StringUtils.join(currExcludeRuns, ";"));
         
                        }

                        // If bait set does not contain comma, the add. Comma means that the pooled normal has two different bait sets. This shouldn't happen, So I'm not adding them. 
                        String thisBait = tempHashMap.get("CAPTURE_BAIT_SET");
                        if(! thisBait.contains(",")){
                            SampleListToOutput.put(pNorm_name, tempHashMap);
                        }
                    }

                }
                // POOLED NORMAL END
                
                // is xenograft project? 
                if(SampleInfo.isXenograftProject()){
                	requestSpecies = "xenograft";
                }
                
                if(SampleListToOutput.size() != 0) {
                    // Add log messages from SampleInfo
                    log_messages.addAll(SampleInfo.getLogMessages());

                    if(SampleInfo.exitLater()){
                        exitLater=true;
                    }

                    // Grab Sample Renames
                    sampleRenames = SampleInfo.getSampleRenames();
                    //SampleListToOutput = checkForSampleSwaps( SampleListToOutput);

                    // Grab sample information form queryProjectInfo
                    ArrayList<String> pInfo = new ArrayList<String>(Arrays.asList(queryProjInfo(apiUser, drm, requestID)));
                    
                    if(! pi.equals("null") && ! invest.equals("null")){
                        runNum = getRunNumber(requestID, pi, invest);
                        if(runNum > 1 && ReqType.equals("rnaseq") ){
                            // Mono wants me to archive these, because sometimes Nick manually changes them.
                            // Get dir of final project location
                            // IF request file is there, search for date of last update
                            // Then copy to archive
                            String finalDir = String.valueOf(projectFilePath).replaceAll("drafts", ReqType);
                            File oldReqFile = new File(finalDir + "/Proj_" + requestID + "_request.txt");
                            if (oldReqFile.exists() && ! force){
                                String lastUpdated = getPreviousDateOfLastUpdate(oldReqFile);
                                copyToArchive(finalDir,requestID, lastUpdated, "_old");
                            } 
                        }
                    }
                    pInfo.add("NumberOfSamples: " + String.valueOf(numSamples));
                    
                    // Only grab values from SampleListToOutput to make an array (You don't need sample) (easier to iterate?)
                    ArrayList<LinkedHashMap<String,String>> sampInfo = new  ArrayList<LinkedHashMap<String,String>>(SampleListToOutput.values());
                    assignProjectSpecificInfo(sampInfo);
                    pInfo.add("Species: " + requestSpecies);
                    
                    // This is done for the *smart* pairing (if necessary) and the grouping file (if necessary)
                    LinkedHashMap<String, Set<String>> patientSampleMap = new LinkedHashMap<String, Set<String>>();
                    
                    // make arrayListof hashmaps of sampleInfo, but only if they are !SAMPLE_CLASS.contains(Normal)
                    ArrayList<LinkedHashMap<String,String>> tumorSampInfo = new ArrayList<LinkedHashMap<String,String>>();
                    for(LinkedHashMap<String,String> tempSamp: sampInfo){
                        if(! tempSamp.get("SAMPLE_CLASS").contains("Normal")){
                            tumorSampInfo.add(tempSamp);
                        }
                    }

                    // Grab readme info - extra readme info is when there are samples with low coverage. It was requested to be saved in the readme file
                    String readmeInfo =  request.getStringVal("ReadMe", apiUser) + " " + extraReadmeInfo;
                    sampInfo = getManualOverrides(readmeInfo, sampInfo);

                    if(ReqType.equals("rnaseq") || ReqType.equals("other")){
                        projDir = new File(projectFilePath);
                        if (!projDir.exists() ) {
                            projDir.mkdir();
                        }
                        print("[INFO] Path: " + projDir);

                        if (!force ) {
                            if (manualDemux) {
                                print("[WARNING] Manual demux performed. I will not output maping file");
                            } else {
                                printMappingFile(SamplesAndRuns, requestID, projDir, baitVersion);
                            }
                        }

                        printRequestFile(pInfo, ampType, libType, strand, requestID, projDir);
                        //sendCompletionEmail(requestID, projDir.getAbsolutePath());
                        printReadmeFile(readmeInfo, requestID, projDir);
                        
                        // criteria for making sample key excel
                        if(ReqType.equals("rnaseq") && !libType.contains("TruSeq Fusion Discovery") && sampInfo.size() > 1){
                            createSampleKeyExcel(requestID, projDir, SampleListToOutput); 
                            // send sample key excel in an e-mail.
                            //emailSampKeyComparisons(requestID, projDir, pInfo);
                        }
                    }else { //if(ReqType.equals("impact")) {   OR EXOME
                        patientSampleMap = createPatientSampleMapping(SampleListToOutput);

                        File manDir = new File("manifests/Proj_" + requestID);
                        if (draft) {
                            manDir = new File(projectFilePath);
                        }
                        if (!manDir.exists() ) {
                            manDir.mkdirs();
                        }

                        // Add bait Version to pInfo
                        if(ReqType.equals("rnaseq")){
                            pInfo.add("DesignFile: NA");
                            pInfo.add("Assay: NA");
                            pInfo.add("TumorType: NA");
                        } else{
                            // Get Design File
                            String[] designs = baitVersion.split("\\+");
                            if(designs.length > 1){
                                pInfo.add("DesignFile: " + findDesignFile(designs[0]));
                                pInfo.add("SpikeinDesignFile: " + findDesignFile(designs[1]));
                            } else if(ReqType.equals("impact")){
                                pInfo.add("DesignFile: " + findDesignFile(baitVersion));
                                pInfo.add("SpikeinDesignFile: NA");
                            } else { // exome
                                pInfo.add("AssayPath: " + findDesignFile(baitVersion));
                            }
                            if(! baitVersion.equals("#EMPTY")){
                                pInfo.add("Assay: " + baitVersion);
                            } else {
                                pInfo.add("Assay: NA");
                            }

                            // Grab tumor type!
                            HashSet<String> tType = new HashSet<String>();
                            for(LinkedHashMap<String,String> tempSamp: sampInfo){
                                String t = tempSamp.get("ONCOTREE_CODE");
                                if(! t.equals("Tumor") && ! t.equals("Normal") && ! t.equals("na") && ! t.equals("Unknown") && ! t.equals("#EMPTY")){
                                    tType.add(t);
                                }
                            }
                            if(tType.isEmpty()){
                                print("[WARNING] I can't figure out the tumor type of this project. ");
                                
                            }else if (tType.size() == 1){
                                pInfo.add("TumorType: " + new ArrayList(tType).get(0));
                            }else if (tType.size() > 1){
                                pInfo.add("TumorType: mixed");// + tType);
                            } else {
                                pInfo.add("TumorType: NA");
                            }
                        }


                        String mapping_filename = manDir + "/Proj_" + requestID + "_sample_mapping.txt";
                        String Manifest_filename =  manDir + "/Proj_" + requestID + "_sample_manifest.txt";
                        String pairing_filename =  manDir + "/Proj_" + requestID + "_sample_pairing.txt";
                        String grouping_filename = manDir + "/Proj_" + requestID + "_sample_grouping.txt";                        
                        String patient_filename = manDir + "/Proj_" + requestID + "_sample_patient.txt";
                        String clinical_filename = manDir + "/Proj_" + requestID + "_sample_data_clinical.txt";

                        if (!force) {
                            printMappingFile(SamplesAndRuns, requestID, manDir, baitVersion);
                        }

                        printRequestFile(pInfo, requestSpecies, requestID, manDir);
                        printHashMap(sampInfo, Manifest_filename);
                        printFileType(sampInfo, patient_filename, "patient");
                        printFileType(tumorSampInfo, clinical_filename, "data_clinical");

                        printGroupingFile(SampleListToOutput,patientSampleMap,grouping_filename);
                        printPairingFile(SampleListToOutput, drm, apiUser, pairing_filename, patientSampleMap);
                        printReadmeFile(readmeInfo, requestID, manDir);        
                    }

                    // if there is an error in the running of this script
                    // delete all but mapping and request. 
                    // IF there was a mapping issue and the mapping file may be wrong, change the name of the mapping file
                    // Also (TODO) add a specifc file with the errors causing the mapping issues
                    // For RNASEQ and other, nothing gets output except request and mapping files
                    // I will have to wait and figure out what to do, but int he meantime I will delete everything
                    if (exitLater && !krista && !requestID.startsWith("05500") && !ReqType.equals("rnaseq") && !ReqType.equals("other")){
                        for (File  f : filesCreated){
                            if(f.getName().endsWith("sample_mapping.txt")){ 
                                File newName = new File(f.toString().replace(".txt", ".error"));
                                if (mappingIssue){
                                    Files.move(f.toPath(), newName.toPath(), REPLACE_EXISTING);
                                } else if (newName.exists()){
                                    newName.delete();
                                }
                            }
                            if( !f.getName().endsWith("sample_mapping.txt") && !f.getName().endsWith("request.txt")){
                                //System.out.println("DELETING: " + f.getName());
                                f.delete();
                            }
                        }
                    }

                    // if this is not a shiny run, and the project stuff is valid, copy to archive and add/append log files.
                    if(!shiny && !krista ){
                        DateFormat dateFormat2 = new SimpleDateFormat("yyyyMMdd");
                        String date = dateFormat2.format(new Date()); 
                        copyToArchive(projectFilePath,requestID, date);
                        printToPipelineRunLog(requestID);
                    }
                }

            }// end of request
        }
        catch(NotFound nf){
            nf.printStackTrace(console);
        }
        catch(IoError | RemoteException ioe){
            ioe.printStackTrace(console);
        }
        catch (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        } finally {

           if(outLogDir != null){
               DateFormat dateFormat = new SimpleDateFormat("dd-MM-yy");
               File filename = new File(outLogDir.getAbsolutePath() + "/Log_" + dateFormat.format(new Date()) + ".txt");

               printLogMessages(filename);
           }
 
           File f = new File(projectFilePath);
           setPermissions(f);
        }
        return ;
    }

    private ArrayList<LinkedHashMap<String,String>> getManualOverrides(String readmeInfo, ArrayList<LinkedHashMap<String,String>> sampInfo){
        // Manual overrides are found in the readme file:
        // Current Manual overrides "OVERRIDE_BAIT_SET" - resents all bait sets (and assay) as whatever is there.
        // TODO: when list of overrides gets bigger, make it a list to search through.

        String[] lines = readmeInfo.split("\n");
        for(String line: lines){
            if(line.startsWith("OVERRIDE_BAIT_SET")){
                String[] overrideSplit = line.split(":");
                baitVersion = overrideSplit[overrideSplit.length -1].trim();
                setNewBaitSet(sampInfo);
            }
        }
        return sampInfo;
    }

    private ArrayList<LinkedHashMap<String,String>> setNewBaitSet(ArrayList<LinkedHashMap<String,String>> sampInfo){
        String newBaitset="#EMPTY";
        String newSpikein="na";
        if(baitVersion.contains("+")){
            String[] bv_split = baitVersion.split("\\+");
            newBaitset=bv_split[0];
            newSpikein=bv_split[1];
        }else{
            newBaitset=baitVersion;
        }

        for (LinkedHashMap<String,String> tempSamp: sampInfo) {
            tempSamp.put("BAIT_VERSION", baitVersion);
            tempSamp.put("CAPTURE_BAIT_SET", newBaitset);
            tempSamp.put("SPIKE_IN_GENES", newSpikein);
        } 
        return sampInfo;
    }

    private void initializeFilePermissions(){
        //add owners permission
        DIRperms.add(PosixFilePermission.OWNER_READ);
        DIRperms.add(PosixFilePermission.OWNER_WRITE);
        DIRperms.add(PosixFilePermission.OWNER_EXECUTE);
        //add group permissions
        DIRperms.add(PosixFilePermission.GROUP_READ);
        DIRperms.add(PosixFilePermission.GROUP_WRITE);
        DIRperms.add(PosixFilePermission.GROUP_EXECUTE);
        //add others permissions
        DIRperms.add(PosixFilePermission.OTHERS_READ);
        DIRperms.add(PosixFilePermission.OTHERS_EXECUTE);

        //add owners permission
        FILEperms.add(PosixFilePermission.OWNER_READ);
        FILEperms.add(PosixFilePermission.OWNER_WRITE);
        //add group permissions
        FILEperms.add(PosixFilePermission.GROUP_READ);
        FILEperms.add(PosixFilePermission.GROUP_WRITE);
        //add others permissions
        FILEperms.add(PosixFilePermission.OTHERS_READ);
 
        return;
    }

    private void setPermissions(File f){
       try{ 
       if(f.isDirectory()){
           try{
               Files.setPosixFilePermissions(f.toPath(),DIRperms);
           } catch (AccessDeniedException e){
               //System.out.println("Access Denied Exception: You probably don't own " + f.toString() + " and cannot change the permissions");
           }
           for( File x : f.listFiles()){
               if( x.isDirectory() ){
                   try{
                       Files.setPosixFilePermissions(Paths.get(x.getAbsolutePath()), DIRperms);
                   } catch (AccessDeniedException e){
                       //System.out.println("Access Denied Exception: You probably don't own " + x.toString() + " and cannot change the permissions");
                   }
                   setPermissions(x);
               } else {
                   try{
                       Files.setPosixFilePermissions(Paths.get(x.getAbsolutePath()), FILEperms);
                   } catch (AccessDeniedException e){
                       //System.out.println("Access Denied Exception: You probably don't own " + x.toString() + " and cannot change the permissions");
                   }
               }
           }
       }
       } catch (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
       }
       return;
    } 

    private String getRecipe(DataRecordManager drm, User apiUser, DataRecord request){
        //this will get the recipe for all sampels under request 
        List<DataRecord> samples = new ArrayList<DataRecord>();
        List<Object> recipe = new ArrayList<Object>();
        try{
            samples = Arrays.asList(request.getChildrenOfType("Sample", apiUser));
            recipe = drm.getValueList(samples, "Recipe", apiUser);

        }catch(Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }

        Set<String> recipes = new HashSet<String>();
        for (Object val : recipe){
            recipes.add((String)val);
        }

        String uniqRecipes = StringUtils.join(recipes, ",");
        return uniqRecipes;
    
    }

    private Map<String, Set<String>> checkPostSeqQC(User apiUser, DataRecordManager drm, DataRecord request,  Map<String, Set<String>> SampleRunHash){
        // This will look through all post seq QC records
        // For each, if sample AND run is in map
        // then check the value of PostSeqQCStatus
        // if passing, continue
        // if failed, output WARNING that this sample failed post seq qc and WHY
        //            remove the run id

        // I'm going to add Sample ID and run ID here when I'm done yo! That way I can check and see if any
        // Don't have PostSeqAnalysisQC
        Map<String, Set<String>> finalList= new HashMap<String, Set<String>>(); 

        try{
            List<DataRecord> postQCs = request.getDescendantsOfType("PostSeqAnalysisQC", apiUser);
            
            for(DataRecord post : postQCs){
                String sampID = post.getStringVal("OtherSampleId", apiUser);

                if(SampleRunHash.containsKey(sampID)){
                    Set<String> runList = SampleRunHash.get(sampID);
                    String[] runParts = post.getStringVal("SequencerRunFolder", apiUser).split("_");
                    String runID = runParts[0] + "_" + runParts[1];

                    if(runList.contains(runID)){
                        String status = String.valueOf(post.getPickListVal("PostSeqQCStatus", apiUser));
                        if(! status.equals("Passed")){
                            String note = post.getStringVal("Note", apiUser);
                            note = note.replaceAll("\n", " ");
                            print("[WARNING] Sample " + sampID + " in run " + runID + " did not pass POST Sequencing QC(" + status + "). The note attached says: " + note + ". This will not be included in manifest.");
                        }else{
                            if( ! finalList.containsKey(sampID) ){
                                Set<String> temp = new HashSet<String>();
                                temp.add(runID);
                                finalList.put(sampID, temp);
                            } else {
                                Set<String> temp = finalList.get(sampID);
                                temp.add(runID);
                                finalList.put(sampID, temp);
                            }
                        }
                        runList.remove(runID);
                    }

                    if(runList.isEmpty()){
                        SampleRunHash.remove(sampID);
                    }
                }

            }   

        } catch(RemoteException ioe){
            ioe.printStackTrace(console);
        } catch(NotFound nf){
            nf.printStackTrace(console);
        }
        if(! SampleRunHash.isEmpty()){
            for(String sampID: SampleRunHash.keySet()){
                Set<String> runIDs = SampleRunHash.get(sampID);
                print("[WARNING] Sample " + sampID + " has runs that do not have POST Sequencing QC. We won't be able to tell if they are failed or not: " + Arrays.toString(runIDs.toArray()) + ". They will still be added to the sample list.");
                if( ! finalList.containsKey(sampID) ){
                    Set<String> temp = new HashSet<String>();
                    temp.addAll(runIDs);
                    finalList.put(sampID, temp);
                } else {
                    Set<String> temp = finalList.get(sampID);
                    temp.addAll(runIDs);
                    finalList.put(sampID, temp);
                }
            }
        }

    return finalList;

    }

    private void checkReadCounts(){
        // For each sample in readsForSample if the number of reads is less than 1M, print out a warning
        for(String sample : readsForSample.keySet()){ 
            Integer numReads = readsForSample.get(sample);
            if( numReads < 1000000){
                // Print AND add to readme file
                extraReadmeInfo += "\n[WARNING] sample " + sample + " has less than 1M total reads: " + numReads + "\n";
                print("[WARNING] sample " + sample + " has less than 1M total reads: " + numReads );
            }
        }
        return; 
    } 

    private String[] queryProjInfo(User apiUser, DataRecordManager drm, String requestID){
        // First capture the Project Info output into a Print strem
        // Then change field names as needed, Send the MAP to print (later)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        // IMPORTANT: Save the old System.out!
        PrintStream old = System.out; 
        System.setOut(ps);
        QueryImpactProjectInfo querySheet = new QueryImpactProjectInfo();
        querySheet.queryProjectInfo(apiUser, drm, requestID, ReqType);
        System.out.flush();
        //Set things back to normal
        System.setOut(old); 
        String [] pInfo = baos.toString().split("\n");

        pi = querySheet.getPI().split("@")[0];
        invest = querySheet.getInvest().split("@")[0];

        return pInfo;
    }

    private LinkedHashMap<String, LinkedHashMap<String, String>> checkForSampleSwaps(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput) {
        Set<String> oldNames = sampleRenames.keySet();
        for (String on : sampleRenames.keySet()){
            if( sampleRenames.containsKey( sampleRenames.get(on) ) && sampleRenames.containsValue(on) && sampleRenames.containsKey(sampleRenames.get(sampleRenames.get(on)))){
                // This means a sample swap is happening.
                if(on == sampleRenames.get(on)){
                    continue;
                }
                
                if(sampleSwap.containsKey(on)){
                    print("[ERROR] Sample is being swapped twice, this doesn't make sense. Nothing has been swapped!");
                    sampleSwap.clear();
                    return SampleListToOutput;
                } else if(! SampleListToOutput.containsKey(on)){
                    print("[ERROR] Sample Swap detected, but old name: "  + on + " not found in Sample List. Nothing has been swapped!");
                    sampleSwap.clear();
                    return SampleListToOutput;
                } else if(! SampleListToOutput.containsKey(sampleRenames.get(on))){
                    print("[ERROR] Sample Swap detected, but new name: "  + on + " not found in Sample List. Nothing has been swapped!");
                    sampleSwap.clear();
                    return SampleListToOutput;
                }else{
                    sampleSwap.put(on, sampleRenames.get(on));
                }
            }
        }

        if(sampleSwap.size() > 0){
            LinkedHashMap<String, LinkedHashMap<String, String>> NewSampleInfos = performSampleSwap(SampleListToOutput);
            
        }

        

        return SampleListToOutput;
    }

    private LinkedHashMap<String, LinkedHashMap<String, String>> performSampleSwap(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput) {
        LinkedHashMap<String, LinkedHashMap<String, String>> tempSampInfoList = new LinkedHashMap<String, LinkedHashMap<String, String>>();

        // This will get the NEW sample's cmo info, and put it with the old sample's QC info.
        for(String on: sampleSwap.keySet()){
            // first REMOVE from sampleRenames:
            sampleRenames.remove(on);

            print("[INFO] Performing sample swap from " + on + " to " + sampleSwap.get(on));
            LinkedHashMap<String, String> swappedInfo = new LinkedHashMap<String, String>(SampleListToOutput.get(on));
            LinkedHashMap<String, String> newInfo = new LinkedHashMap<String, String>(SampleListToOutput.get(sampleSwap.get(on)));
            for(String val : sampleSwapValues){
                swappedInfo.put(val, newInfo.get(val));
            }
            
            tempSampInfoList.put(sampleSwap.get(on), swappedInfo); 
        }

        // Now just replace whatever was in SampleListToOutput with the temp overlap
        for(String name : tempSampInfoList.keySet()){
            SampleListToOutput.put(name, tempSampInfoList.get(name));
        }

        return SampleListToOutput;
    }

    private Map<String, Set<String>> addPooledNormalstoSampleList(Map<String, Set<String>> SamplesAndRuns, Map<String, String> RunID_and_PoolName, HashMap<DataRecord,HashSet<String>> PooledNormalSamples, User apiUser){

        HashSet<String> runs = new HashSet<String>();
        for (DataRecord rec : PooledNormalSamples.keySet()){
            try{
                String sampName = rec.getStringVal("OtherSampleId", apiUser);
                HashSet<String> pools = PooledNormalSamples.get(rec);
                for(String pool : pools){
                    for(String runID :RunID_and_PoolName.keySet()){
                        if (RunID_and_PoolName.get(runID).contains(pool)){
                            runs.add(runID);
                        }
                    }
                }
                Set<String> t = new HashSet<String>();
                if(SamplesAndRuns.containsKey(sampName)){
                    t.addAll(SamplesAndRuns.get(sampName));
                }
                t.addAll(runs);
                SamplesAndRuns.put(sampName, t);
                runs.clear();
            } catch (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
            }
        }

        return SamplesAndRuns;
    } 
     
    private String assignProjectSpecificInfo(ArrayList<LinkedHashMap<String,String>> sampInfo){
        // This will iterate the samples, grab the species, and if it is not the same and it
        // is not xenograft warning will be put.
        // If the species has been set to xenograft, it will give a warning if species is not human or xenograft
        Boolean bvChanged=false;
        for (LinkedHashMap<String,String> tempSamp: sampInfo) {
            
            // Skip pooled normal stuff
            if(tempSamp.get("SAMPLE_TYPE").equals("NormalPool") || tempSamp.get("SPECIES").equals("POOLNORMAL")){
                continue;
            }

            String sp = tempSamp.get("SPECIES");
            // SPECIES CHECKING:
            if(! sp.isEmpty() && sp != null){
                if (requestSpecies == "xenograft"){
                	// Xenograft projects may only have samples of species human or xenograft
                    if( !sp.equals("Human") && sp != "xenograft"){
                        print("[ERROR] Request species has been determined as xenograft, but this sample is neither xenograft or human: " + sp);
                    }
                }else if(requestSpecies == "#EMPTY") {
                    requestSpecies = sp;
                }
                else if( !requestSpecies.equals(sp)){
                	// Requests that are not xenograft must have 100% the same species for each sample. If that is not true, it will output issue here:
                    print("[ERROR] There seems to be a clash between species of each sample: Species for sample " + tempSamp.get("IGO_ID") + "="+sp+ " Species for request so far="+requestSpecies);
                } 
            }
            
            //baitVerison - sometimes bait version needs to be changed. If so, the CAPTURE_BAIT_SET must also be changed
            if(ReqType == "rnaseq" || ReqType == "other"){
                baitVersion="#EMPTY";
            } else {
                String bv = tempSamp.get("BAIT_VERSION");
                if(bv != null && !bv.isEmpty()){
                    if(ReqType == "exome"){
                    	// First check xenograft, if yes, then if bait version is Agilent (manual bait version for exomes) change to xenograft version of Agilent
                    	if(requestSpecies.equals("xenograft") && bv.equals("AgilentExon_51MB_b37_v3")){
                    		bv = "AgilentExon_51MB_b37_mm10_v3";
                    		bvChanged = true;
                    	}
                        String bv_sp = bv;
                        // Test Bait version. 
                        if(findDesignFile(bv) == "NA"){
                            // Add species to end of bv
                            if(requestSpecies.toLowerCase().equals("human")){
                                bv_sp = bv + "_" + humanAbrevSpecies;
                            } else if (requestSpecies.toLowerCase().equals("mouse")){
                                bv_sp = bv + "_" + mouseAbrevSpecies;
                            } else if (requestSpecies.toLowerCase().equals("xenograft")){
                            	bv_sp = bv + "_" + humanAbrevSpecies + "_" + mouseAbrevSpecies;
                            }
                            if(bv_sp != bv && findDesignFile(bv_sp) != "NA"){
                                baitVersion = bv_sp;
                                bv= bv_sp;
                                //setNewBaitSet should be called
                                bvChanged = true;
                            }
                        }
                    }
                    if( !baitVersion.equals(bv) && !baitVersion.equals("#EMPTY") ){
                        print("[ERROR] Request Bait version is not consistent: Current sample Bait verion: " + bv + " Bait version for request so far: " + baitVersion);
                        // This should be an error
                    }
                    else if (baitVersion.equals("#EMPTY")){
                        baitVersion = bv;
                    }
                }
            }
        }
        if(bvChanged){
            setNewBaitSet(sampInfo);
        }
        return baitVersion;
    }
    private void getLibTypes (ArrayList<DataRecord> passingSeqRuns, DataRecordManager drm, User apiUser, DataRecord request) {
        try{
            // ONE: Get ancestors of type sample from the passing seq Runs.
            List<List<DataRecord>> samplesFromSeqRun = drm.getAncestorsOfType(passingSeqRuns, "Sample", apiUser);

            // TWO: Get decendants of type sample from the request
            List<DataRecord> samplesFromRequest = request.getDescendantsOfType("Sample", apiUser);

            Set<DataRecord> finalSampleList = new HashSet<DataRecord>();
            // THREE: Get the overlap
            for (List<DataRecord> sampList : samplesFromSeqRun){
                ArrayList<DataRecord> temp = new ArrayList<DataRecord>(sampList);
                temp.retainAll(samplesFromRequest);
                finalSampleList.addAll(temp);
            }

            if(force){
                finalSampleList.addAll(samplesFromRequest);
            }

            // Try finalSampleList FIRST. If this doesn't have any library types, just try samples from seq run.
            checkSamplesForLibTypes(finalSampleList, drm, apiUser, request);

            //if(ReqType.length() == 0) {
            //    finalSampleList.clear();
            //    for(List<DataRecord> seqLine : samplesFromSeqRun){
            //        finalSampleList.addAll(seqLine);
             //   }
            //    checkSamplesForLibTypes(finalSampleList, drm, apiUser, request);

            //    if(ReqType.length() > 1){
            //        print("[ERROR] Unable to correctly resolve request type. No request type protocols found in request, and while searching parent request, multiple request types were found." + StringUtils.join(ReqType, ", "));
            //    }
           // }

        } catch  (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return;
    }

            
    private void checkSamplesForLibTypes( Set<DataRecord> finalSampleList, DataRecordManager drm, User apiUser, DataRecord request) { 
        try{
            // This is where I have to check all the overlapping samples for children of like 5 different types.
            for (DataRecord rec : finalSampleList){
            if(checkValidBool(Arrays.asList(rec.getChildrenOfType("TruSeqRNAProtocol", apiUser)), drm, apiUser)) {
                for (DataRecord rnaProtocol: Arrays.asList(rec.getChildrenOfType("TruSeqRNAProtocol", apiUser))){
                    try{
                        if (rnaProtocol.getBooleanVal("Valid", apiUser)){
                            String exID = rnaProtocol.getStringVal("ExperimentId", apiUser);
                            List<DataRecord> rnaExp =  drm.queryDataRecords("TruSeqRNAExperiment", "ExperimentId='" + exID + "'", apiUser);
                            if(rnaExp.size() != 0){
                                List<Object> strandedness = drm.getValueList(rnaExp, "TruSeqStranding", apiUser);
                                for(Object x: strandedness){
                                    // Only check for Stranded, because older kits were not stranded and did not have this field, ie null"
                                    if(String.valueOf(x).equals("Stranded")){
                                        libType.add("TruSeq Poly-A Selection Stranded");
                                        strand.add("Reverse");
                                        ReqType = "rnaseq";
                                    }else {
                                        libType.add("TruSeq Poly-A Selection Non-Stranded");
                                        strand.add("None");
                                        ReqType = "rnaseq";
                                    }
                                }
                            }
                        }     
                    }catch(NullPointerException e){
                        System.err.println("[WARNING] You hit a null pointer exception while trying to find valid for library types. Please let BIC know.");
                    } catch  (Throwable e) {
                        logger.log(Level.SEVERE, "Exception thrown: ", e);
                        e.printStackTrace(console);
                    }
                     
                }
            }
            if(Arrays.asList(rec.getChildrenOfType("TruSeqRNAsmRNAProtocol4", apiUser)).size() > 0) {
                libType.add("TruSeq smRNA");
                strand.add("");
                ReqType = "rnaseq";
            }
            if(checkValidBool(Arrays.asList(rec.getChildrenOfType("TruSeqRiboDepleteProtocol1", apiUser)), drm, apiUser)) {
                libType.add("TruSeq RiboDeplete");
                strand.add("Reverse");
                ReqType = "rnaseq";
            }
            if(checkValidBool(Arrays.asList(rec.getChildrenOfType("TruSeqRNAFusionProtocol1", apiUser)), drm, apiUser)) {
                libType.add("TruSeq Fusion Discovery");
                strand.add("None");
                ReqType = "rnaseq";
            }
            if(checkValidBool(Arrays.asList(rec.getChildrenOfType("SMARTerAmplificationProtocol1", apiUser)), drm, apiUser)) {
                libType.add("SMARTer Amplification");
                strand.add("None");
                ReqType = "rnaseq";
            }
            if(checkValidBool(Arrays.asList(rec.getChildrenOfType("KAPAmRNAStrandedSeqProtocol1", apiUser)), drm, apiUser)) {
            //if(rec.getChildrenOfType("KAPAmRNAStrandedSeqProtocol1", apiUser).length != 0) {
                libType.add("KAPA mRNA Stranded");
                strand.add("Reverse");
                ReqType = "rnaseq";
            }
            if(rec.getChildrenOfType("NimbleGenHybProtocol2", apiUser).length != 0){
                ReqType = "impact";
            }
            if(rec.getChildrenOfType("KAPAAgilentCaptureProtocol1", apiUser).length != 0){
                ReqType = "exome";
            }
            }
        } catch  (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }   
        return;
        
        
    }
    
    private Boolean checkValidBool(List<DataRecord> recs, DataRecordManager drm, User apiUser){
        if(recs == null || recs.size() == 0){
            return false;
        }

        try{
            List<Object> valids = drm.getValueList(recs, "Valid", apiUser);
            for(Object val : valids){
                if (String.valueOf(val).equals("true")){
                    return true;
                }
            }
        }catch(NullPointerException e){
            System.err.println("[WARNING] You hit a null pointer exception while trying to find valid for library types. Please let BIC know.");
        } catch  (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return false;
    }

    private LinkedHashMap<String, String> getSampleInfoMap (User apiUser, DataRecordManager drm, DataRecord rec, Map<String, Set<String>> SamplesAndRuns, Map RunID_and_PoolName, boolean poolNormal, boolean transfer){
            LinkedHashMap<String, String> RequestInfo = new LinkedHashMap<String, String>();
        try{
            // Latest attempt at refactoring the code. Why does species come up so much?
            Map<String, Object> fieldMap = rec.getFields(apiUser);
            
            SampleInfo LS2;
            if(ReqType.equals("impact") || poolNormal){
                LS2 = new SampleInfoImpact(ReqType, apiUser, drm, rec, SamplesAndRuns, force, poolNormal, transfer, deadLog);
            }else if(ReqType.equals("exome")){
                LS2 = new SampleInfoExome(ReqType, apiUser, drm, rec, SamplesAndRuns, force, poolNormal, transfer, deadLog);
            } else{
                LS2 = new SampleInfo(ReqType, apiUser, drm, rec, SamplesAndRuns, force, poolNormal, transfer, deadLog);
            }
            RequestInfo = LS2.SendInfoToMap();

            if(sampleAlias.keySet().contains( RequestInfo.get("CMO_SAMPLE_ID"))){
                RequestInfo.put("MANIFEST_SAMPLE_ID", sampleAlias.get(RequestInfo.get("CMO_SAMPLE_ID")));
            }

            if(badRuns.keySet().contains( RequestInfo.get("CMO_SAMPLE_ID"))){
               String excludeRuns = StringUtils.join(badRuns.get(RequestInfo.get("CMO_SAMPLE_ID")), ";"); 
               RequestInfo.put("EXCLUDE_RUN_ID", excludeRuns);
            }
            
        } catch(RemoteException ioe){
            ioe.printStackTrace(console);
        }
        
        return RequestInfo;

    }

    private Map<String, Set<String>> getSampSpecificQC(String requestID, DataRecordManager drm, User apiUser) {
        // Here I will grab all Sample Speicfic QC that have request XXXX. 
        // I will then populate a thing that will have all the samples as well as the runs they passed in.

        Map<String, Set<String>> x = new HashMap<String, Set<String>>();

        try{
            List<DataRecord> SampleQCList = drm.queryDataRecords("SeqAnalysisSampleQC", "Request = '" + requestID + "'" , apiUser);

            // Basically do the same thing as the other thing.
            for(DataRecord rec : SampleQCList) {
                String RunStatus = String.valueOf(rec.getPickListVal("SeqQCStatus", apiUser));
                String[] runParts= rec.getStringVal("SequencerRunFolder", apiUser).split("_");
                String RunID = runParts[0] + "_" + runParts[1];
                String SampleID = rec.getStringVal("OtherSampleId", apiUser);
                sampleRuns.add(RunID);

                if(! RunStatus.contains("Passed")) {
                    if(RunStatus.contains("Failed")){
                        print("[SAMPLE_QC_INFO] Not including Sample " + SampleID + " from Run ID " + RunID + " because it did NOT pass Sequencing Analysis QC: " + RunStatus);
                        addToBadRuns(SampleID, RunID);
                    } else if (RunStatus.contains("Under-Review")) {
                        print("[SAMPLE_QC_ERROR] Sample " + SampleID + " from RunID " + RunID + " is still under review. I cannot guarantee this is DONE!");
                        exitLater = true;
                        mappingIssue = true;
                    } else {
                        print("[SAMPLE_QC_ERROR] Sample " + SampleID + " from RunID " + RunID + " needed additional reads. This status should change when the extra reads are sequenced. Please check. ");
                        // continue; //(?)
                        exitLater = true;
                        mappingIssue=true;
                    }
                    continue;
                }

                //PassingseqRuns - this is used to grab library type.
                passingSeqRuns.add(rec);
                int totalReads=0;
                // Here I am going to pull the read count!
                try{
                    totalReads = (int) (long) rec.getLongVal("TotalReads", apiUser);
                } catch (NullPointerException e ){
                }
                if(readsForSample.containsKey(SampleID)){
                    Integer tempReads = readsForSample.get(SampleID) + totalReads;
                    readsForSample.put(SampleID, tempReads);
                }else{
                    readsForSample.put(SampleID, totalReads);
                }

                String alias = rec.getStringVal("SampleAliases", apiUser);

               // NOT DOING ANYTHING WITH ALIAS RIGHT NOW
               if( alias != null && ! alias.isEmpty()){
                   print("[WARNING] SAMPLE " + SampleID + " HAS AN ALIAS: " + alias + "!");
                   sampleAlias.put(SampleID, alias);
               }

                if(x.containsKey(SampleID)){
                    Set<String> temp = x.get(SampleID);
                    temp.add(RunID);
                    x.put(SampleID, temp);
                } else{
                    Set<String> temp = new HashSet<String>();
                    temp.add(RunID);
                    x.put(SampleID, temp);
                }
                
            }


        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return x;
    }
  
    private void addToBadRuns(String sample, String run){
        if(badRuns.containsKey(sample)){
            Set<String> temp = badRuns.get(sample);
            temp.add(run);
            badRuns.put(sample, temp);
        }else{
            Set<String> temp = new HashSet<String>();
            temp.add(run);
            badRuns.put(sample, temp);
        }
    
    }

    private Map<String, Set<String>> compareSamplesAndRuns (Map<String, Set<String>> pool, Set<String> poolRuns, Map<String, Set<String>> sample, Set<String> sampleRuns, String requestID, boolean manualDemux){
        // If sample and pool have the same number of samples and runIDs, return sample (more accurate information)
	// If pool has more RunIDs than sample, then use pool's data, with whatever sample specific overlap available.
	//     write warning about it
        // If sample has more run IDs than pool, send an error out because that should never happen.

        Map<String, Set<String>> FinalSamplesAndRuns = new HashMap<String, Set<String>>();
        FinalSamplesAndRuns.putAll(sample);

        if(FinalSamplesAndRuns.size() == 0){
            if(pool.size() != 0 && ! manualDemux ){
                print("[ERROR] For this project the sample specific QC is not present. My script is looking at the pool specific QC instead, which assumes that if the pool passed, every sample in the pool also passed. This is not necessarily true and you might want to check the delivery email to make sure it includes every sample name that is on the manifest. This doesn’t mean you can’t put the project through, it just means that the script doesn’t know if the samples failed sequencing QC.");
                mappingIssue=true;
            }
            print(poolQCWarnings);
        }
        else if(poolQCWarnings.contains("ERROR")){
            print(poolQCWarnings);
        }
        if(poolRuns.equals(sampleRuns)){
            return FinalSamplesAndRuns;
        }

        // Start with sample data, go through pool and add any run Ids for samples that don't have them...
        Set<String> TempRunList = new HashSet<String>();
        TempRunList.addAll(poolRuns);

        for(String run : sampleRuns){
            TempRunList.remove(run);
        }

        if(TempRunList.size() > 0){
            print("[SAMPLE_QC_INFO] Sample specific QC is missing run(s) that pool QC contains. Will add POOL QC data for missing run(s): " + StringUtils.join(TempRunList, ", " ));
            String msg = "[SAMPLE_QC_INFO] Sample specific QC is missing run(s) that pool QC contains for Project: " + requestID + " Runs: " + StringUtils.join(TempRunList, ", " );
            logger.log(Level.INFO, msg);
 
            int added = 0;
            for(String samp : pool.keySet()){
                // get run ids from pool sample that overlap with the ones sample qc is missing.
                Set<String> Temp2 = new HashSet<String>();
                Temp2.addAll(pool.get(samp));
                Temp2.retainAll(TempRunList);
    
                if(Temp2.size() > 0){
                    if(FinalSamplesAndRuns.containsKey(samp)){
                        Set<String> temp = FinalSamplesAndRuns.get(samp);
                        temp.addAll(Temp2);
                        FinalSamplesAndRuns.put(samp,temp);
                    } else {
                        Set<String> temp = new HashSet<String>();
                        temp.addAll(Temp2);
                        FinalSamplesAndRuns.put(samp,temp);
                    }
                }
            }
            
        }

        // Here go through and see if any runs are included in Sample QC and not Pool QC.
        TempRunList.clear();
        TempRunList.addAll(sampleRuns);

        for(String run : poolRuns){
            TempRunList.remove(run);
        }
       
        if(TempRunList.size() > 0){
            //print("[POOL_QC_ERROR] Run(s) were found in sample level QC that are not in pool level QC: " + StringUtils.join(TempRunList, ", " ));
        }

        return FinalSamplesAndRuns;

    }

    private Map<String, Set<String>> getPoolSpecificQC(DataRecord request, DataRecordManager drm, User apiUser) {
        Map<String, Set<String>> SamplesAndRuns = new HashMap<String, Set<String>>();
        try{
            String reqID = request.getStringVal("RequestId", apiUser);
            // Get the first sample data records for the request
            ArrayList<DataRecord> originalSampRec = new ArrayList<DataRecord>();
            ArrayList<DataRecord> samp = new ArrayList<DataRecord>(request.getDescendantsOfType("Sample", apiUser));
            for (DataRecord s : samp){
                ArrayList<DataRecord> reqs = new ArrayList<DataRecord>( s.getParentsOfType("Request", apiUser));
                ArrayList<DataRecord> plates = new ArrayList<DataRecord>(s.getParentsOfType("Plate",apiUser));
                if(! reqs.isEmpty() ){
                    originalSampRec.add(s);
                } else if(! plates.isEmpty() ){
                    for(DataRecord p : plates){
                        if(p.getParentsOfType("Request", apiUser).size() != 0){
                            originalSampRec.add(s);
                            break;
                        }
                    }
                }
            }

            // For each run qc, find the sample 
            ArrayList<DataRecord> sequencingRuns = new ArrayList<DataRecord>(request.getDescendantsOfType("SeqAnalysisQC", apiUser));
            for(DataRecord seqrun : sequencingRuns){

                if( ! verifySeqRun(seqrun, reqID, apiUser )){
                    continue;
                }
                String RunStatus = String.valueOf(seqrun.getPickListVal("SeqQCStatus", apiUser));

                if (RunStatus.isEmpty()) {
                    RunStatus = String.valueOf(seqrun.getEnumVal("QCStatus", apiUser));
                }

                // Try to get RunID, if not try to get pool name, if not again, then error!
                String RunID="null";
                String runPath = seqrun.getStringVal("SequencerRunFolder", apiUser).replaceAll("/$", "");
                if(runPath.length() > 0){
                    String[] pathList = runPath.split("/");
                    String runName = pathList[(pathList.length - 1)];
                    String pattern = "^(\\d)+(_)([a-zA-Z]+_[\\d]{4})(_)([a-zA-Z\\d\\-_]+)";
                    RunID = runName.replaceAll(pattern, "$3");
                } else {
                    RunID = seqrun.getStringVal("SampleId", apiUser);
                }

                if((! RunStatus.contains("Passed")) && (! RunStatus.equals("0"))){
                    if(RunStatus.contains("Failed")){
                        poolQCWarnings += "[POOL_QC_INFO] Skipping Run ID " + RunID + " because it did NOT pass Sequencing Analysis QC: " + RunStatus + "\n";
                        poolRuns.add(RunID);
                        continue;
                    } else if (RunStatus.contains("Under-Review")) {
                        String pool = seqrun.getStringVal("SampleId", apiUser);
                        poolQCWarnings += "[POOL_QC_ERROR] RunID " + RunID + " is still under review for pool " + pool + " I cannot guarantee this is DONE!\n";
                        exitLater = true;
                        mappingIssue=true;
                    } else {
                        poolQCWarnings += "[POOL_QC_ERROR] RunID " + RunID + " needed additional reads. I cannot tell yet if they were finished. Please check. \n";
                        poolRuns.add(RunID);
                        exitLater = true;
                        mappingIssue=true;
                    }
                }else{
                     poolRuns.add(RunID);
                }
                passingSeqRuns.add(seqrun);
    
                if(RunID == "null") {
                    poolQCWarnings += "[POOL_QC_ERROR] Unable to find run path or related sample ID for this sequencing run.\n";
                    exitLater = true;
                    mappingIssue=true;

                }
    
                RunID_and_PoolName = linkPoolToRunID(RunID_and_PoolName, RunID, seqrun.getStringVal("SampleId", apiUser));
    
                // Populate Samples and Runs 
                List<DataRecord> samplesFromSeqRun = seqrun.getAncestorsOfType("Sample", apiUser);
                samplesFromSeqRun.retainAll(originalSampRec);
    
                for (DataRecord s1: samplesFromSeqRun){
                    String sname = s1.getStringVal("OtherSampleId", apiUser);
                    if(SamplesAndRuns.containsKey(sname)){
                        Set<String> temp = SamplesAndRuns.get(sname);
                        temp.add(RunID);
                        SamplesAndRuns.put(sname, temp);
                    } else{
                        Set<String> temp = new HashSet<String>();
                        temp.add(RunID);
                        SamplesAndRuns.put(sname, temp);
                    }
                }
            }
        } catch(Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return SamplesAndRuns;
    }

     private boolean verifySeqRun(DataRecord seqrun, String reqID, User apiUser){
          // This is to verify this sequencing run is found 
          // Get the parent sample data type
          // See what the request ID (s) are, search for reqID.
          // Return (is found?)
     
          boolean sameReq = false;
     
          try{
               List<DataRecord> parentSamples = seqrun.getParentsOfType("Sample", apiUser);
               for(DataRecord p : parentSamples){
                    String reqs = p.getStringVal("RequestId", apiUser);
                    List<String> requests = Arrays.asList(reqs.split("\\s*,\\s*"));
                    sameReq = requests.contains(reqID);
               }
          } catch(Throwable e) {
                    logger.log(Level.SEVERE, "Exception thrown: ", e);
                    e.printStackTrace(console);
          }
          return sameReq;
     }

    private String getPreviousDateOfLastUpdate(File request){
        String date="NULL";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(request));
            String line;
            while ((line = reader.readLine()) != null) {
                if( line.contains("DateOfLastUpdate: ")){
                    date=line.split(": ")[1].replaceAll("-", "");
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        // DIdn't find anything, return NULL
        return date;

    }

    private void createSampleKeyExcel(String requestID, File projDir, LinkedHashMap<String, LinkedHashMap<String, String>> sampleHashMap){
        // sample info
        File sampleKeyExcel = new File(projDir.getAbsolutePath() + "/Proj_" + requestID + "_sample_key.xlsx");
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

        //create the workbook
        XSSFWorkbook wb =new XSSFWorkbook();

        // Create a sheet in the workbook
        XSSFSheet sampleKey = wb.createSheet("SampleKey");

        //set the row number
        int rowNum = 0;

        // Protect the whole sheet, unlock the cells that need unlocking
        sampleKey.protectSheet("banannaGram72");

        CellStyle unlockedCellStyle = wb.createCellStyle();
        unlockedCellStyle.setLocked(false);

        // First put the directions.
        String instructs = "Instructions:\n    - Fill in the GroupName column for each sample.\n        - Please do not leave any blank fields in this column.\n        - Please be consistent when assigning group names." + 
              "\n        - GroupNames are case sensitive. For example, “Normal” and “normal” will be identified as two different group names.\n        - GroupNames should start with a letter  and only use characters, A-Z and 0-9. " + 
              "Please do not use any special characters (i.e. '&', '#', '$' etc) or spaces when assigning a GroupName.\n    - Please only edit column C. Do not make any other changes to this file.\n        " +
              "- Do not change any of the information in columns A or B.\n        - Do not rename the samples IDs (InvestigatorSampleID or FASTQFileID). If you have a question about the sample names, please email " +
              "bic-request@cbio.mskcc.org.\n        - Do not reorder or rename the columns.\n        - Do not use the GroupName column to communicate any other information (such as instructions, comments, etc)";

        sampleKey=addRowToSheet(wb, sampleKey, new ArrayList<String>(Arrays.asList(instructs)),rowNum, "instructions");
        rowNum ++;

        //header
        sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<String>(Arrays.asList("FASTQFileID", "InvestigatorSampleID", "GroupName")), rowNum, "header");
        rowNum ++;

        ArrayList<String> sids = new ArrayList(sampleHashMap.keySet());
        Collections.sort(sids);

        // add each sample 
        for (String hashKey : sids){
            LinkedHashMap<String, String> hash = sampleHashMap.get(hashKey);
            String investSamp = hash.get("INVESTIGATOR_SAMPLE_ID");
            String seqIGOid = hash.get("SEQ_IGO_ID");
            if (seqIGOid == null){
                seqIGOid = hash.get("IGO_ID");
            }

            String sampName1 = hash.get("CMO_SAMPLE_ID");
            if(sampName1 == null || sampName1.startsWith("#")){

            } 
            
            String cmoSamp =  sampName1 + "_IGO_" + seqIGOid;
            if( cmoSamp == null || cmoSamp.startsWith("#") ){
                cmoSamp = sampName1 + "_IGO_" + seqIGOid;
            } 
            sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<String>(Arrays.asList(cmoSamp, investSamp)), rowNum, null);

            // Unlock this rows third cell
            Row thisRow = sampleKey.getRow(rowNum);
            Cell cell3 = thisRow.createCell(2);  // zero based
            cell3.setCellStyle(unlockedCellStyle);

            rowNum ++;
        }

        // EMPTY cell conditional formatting: color cell pink if there is nothing in it:
        SheetConditionalFormatting sheetCF = sampleKey.getSheetConditionalFormatting();
        ConditionalFormattingRule emptyRule = sheetCF.createConditionalFormattingRule(ComparisonOperator.EQUAL,"\"\"");
        PatternFormatting fill1 = emptyRule.createPatternFormatting();
        fill1.setFillBackgroundColor(IndexedColors.YELLOW.index);

        CellRangeAddress[] regions = { CellRangeAddress.valueOf("A1:C" + rowNum) };
        sheetCF.addConditionalFormatting(regions, emptyRule);

        // DATA VALIDATION
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sampleKey);
        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper.createTextLengthConstraint(ComparisonOperator.GE, "15", null);
        CellRangeAddressList rangeList = new CellRangeAddressList( );
        rangeList.addCellRangeAddress(CellRangeAddress.valueOf("A1:C" + rowNum));
        XSSFDataValidation dv1 = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, rangeList);
        dv1.setShowErrorBox(true);
        sampleKey.addValidationData(dv1);

        // Lastly auto size the three columns I am using:
        sampleKey.autoSizeColumn(0);
        sampleKey.autoSizeColumn(1);
        sampleKey.autoSizeColumn(2); 




        // Add extra sheet called Example that will have the example 
        XSSFSheet exampleSheet = wb.createSheet("Example");
        rowNum=0;
        exampleSheet.protectSheet("banannaGram72");

        // There are a couple different examples so I would like to
        // grab them from a tab-delim text file, and row by row add them to the excel.

        try{
            FileInputStream exStream = new FileInputStream(sampleKeyExFile);
            DataInputStream in = new DataInputStream(exStream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String exLine;

            while ((exLine=br.readLine()) != null){
                String type = null;
                if(exLine.startsWith("Correct")){
                    type = "Correct";
                }else if(exLine.startsWith("Incorrect")){
                    type = "Incorrect";
                }

                String[] cellVals = exLine.split("\t");

                exampleSheet = addRowToSheet(wb, exampleSheet, new ArrayList<String>(Arrays.asList(cellVals)), rowNum, type);
                rowNum ++;
            }

        } catch(Exception e) {
            print("An Exception has Occurred: " + e.getMessage());
        }
        // the example sheet has a header for each example, so I can't auto size that column.
        exampleSheet.setColumnWidth(0, (int) (exampleSheet.getColumnWidth(0) * 2.2));
        //exampleSheet.autoSizeColumn(0);
        exampleSheet.autoSizeColumn(1);
        exampleSheet.autoSizeColumn(2);

        // Now that I think I did this right, Print the excel the same way I did in other methods:
        try{
            //Now that the excel is done, print it to file            
            FileOutputStream fileOUT = new FileOutputStream(sampleKeyExcel);
            filesCreated.add(sampleKeyExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch(Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }


        return;
    }

    private void printPairingExcel(String pairing_filename, LinkedHashMap<String, String> pair_Info, ArrayList<String> exome_normal_list){
        File pairingExcel = new File(pairing_filename.substring(0, pairing_filename.lastIndexOf('.')) + ".xlsx");
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        
        XSSFWorkbook wb =new XSSFWorkbook();
        XSSFSheet pairingInfo = wb.createSheet("PairingInfo");
        int rowNum = 0;
        
        pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<String>(Arrays.asList("Tumor","MatchedNormal","SampleRename")), rowNum, "header");
        rowNum ++;

        for( String tum : pair_Info.keySet()){
            String norm = pair_Info.get(tum);
            if(ReqType == "exome" && exome_normal_list.contains(tum)){
                norm= tum;
                tum = "na";
            }

            pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<String>(Arrays.asList(tum,norm)), rowNum, null);
            rowNum ++;
        }
        
        try{ 
            //Now that the excel is done, print it to file            
            FileOutputStream fileOUT = new FileOutputStream(pairingExcel);
            filesCreated.add(pairingExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch(Throwable e) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return;
    }
    
    private void printHashMap(ArrayList<LinkedHashMap<String,String>> Hmap, String filename) {
        try{
        char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
        String ExcelFileName = filename.substring(0, filename.lastIndexOf('.')) + ".xlsx";
        File manifest = new File(filename); 
        //PrintWriter pW = new PrintWriter(new FileWriter(manifest, false), false);
        XSSFWorkbook wb =new XSSFWorkbook();
        XSSFSheet sampleInfo = wb.createSheet("SampleInfo");
        int rowNum = 0;

        // Quickly make SampleRenames sheet, fill in correctly with NOTHing in it
        XSSFSheet sampleRenames =wb.createSheet("SampleRenames");
        sampleRenames = addRowToSheet(wb, sampleRenames, new ArrayList<String>(Arrays.asList("OldName", "NewName")), rowNum, "header");
        
        if(NewMappingScheme == 1){
            for(LinkedHashMap<String, String> row : Hmap){
                rowNum++;
                ArrayList<String> replaceNames = new ArrayList<String>(Arrays.asList(row.get("MANIFEST_SAMPLE_ID"),row.get("CORRECTED_CMO_ID")));
                sampleRenames = addRowToSheet(wb, sampleRenames, replaceNames, rowNum, null);
            }
        }

        rowNum = 0;

        String[] header0 = hashMapHeader.toArray(new String[hashMapHeader.size()]);
        ArrayList<String> header = new ArrayList<String>(Arrays.asList(header0));        

        // Fixing header names
        ArrayList <String> replace = new ArrayList<String>(Arrays.asList(manualMappingHashMap.split(",")));
        for(String fields: replace){
            String[] parts = fields.split(":");
            int indexHeader = header.indexOf(parts[0]);
            header.set(indexHeader, parts[1]);
        }

        try{
            // Print header:
            //pW.println(StringUtils.join(header, "\t"));
            sampleInfo = addRowToSheet(wb, sampleInfo, header, rowNum, "header");
            rowNum++;

            // output each line, in order!
            for( LinkedHashMap<String, String> row : Hmap) {
                ArrayList<String>vals = new ArrayList<String>(row.values());
                //pW.println(StringUtils.join(vals, "\t"));
                sampleInfo = addRowToSheet(wb, sampleInfo, row, rowNum, null);
                rowNum++;
            }

            //Conditional Format
            //The formula says : IF there is a # in cell, IF the # is in the first position, return true
            //otherwise return false. 
            SheetConditionalFormatting sheetCF = sampleInfo.getSheetConditionalFormatting();
            ConditionalFormattingRule hashTagRule = sheetCF.createConditionalFormattingRule("IF(ISNUMBER(FIND(\"#\",A1)),IF(FIND(\"#\",A1)=1,1,0),0)");
            PatternFormatting fill1 = hashTagRule.createPatternFormatting();
            fill1.setFillBackgroundColor(IndexedColors.PINK.index);

            //Make a range that is at least the dimensions of the Hmap
            // do the header.size() divided by 26. That is how many blah. Then get the remainder, and that is the second letter.
            String lastSpot = "";
            if(header.size() > 26){
                int firstLetter = header.size()/26;
                int remainder = header.size() - (26 * firstLetter);
                lastSpot=alphabet[firstLetter-1] + alphabet[remainder-1] + String.valueOf(Hmap.size() + 1);
            } else {
                lastSpot=alphabet[header.size()-1] + String.valueOf(Hmap.size() + 1); 
            }
            CellRangeAddress[] regions = { CellRangeAddress.valueOf("A1:" + lastSpot) };

            sheetCF.addConditionalFormatting(regions, hashTagRule);

            //Now that the excel is done, print it to file            
            FileOutputStream fileOUT = new FileOutputStream(ExcelFileName);
            wb.write(fileOUT);
            filesCreated.add(new File(ExcelFileName));
            fileOUT.close();  
            
            } catch (Throwable e) {
                logger.log(Level.SEVERE, "Exception thrown: ", e);
                e.printStackTrace(console);
            } //finally {
                //if (pW != null) {
                //    pW.close();
                //}
            //}
        } catch (Throwable e) {
                e.printStackTrace(console);
                logger.log(Level.SEVERE, "Exception thrown: ", e);
        }
        return;
    }

    private XSSFSheet addRowToSheet(XSSFWorkbook wb, XSSFSheet sheet, ArrayList<String> list, int rowNum, String type) {
        try{
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum=0;
            for(String val : list) {
                if(val == null || val.isEmpty()) {
                    val = "#empty";
                }
                XSSFCell cell = row.createCell(cellNum++);
                XSSFCellStyle style = wb.createCellStyle();
                XSSFFont headerFont = wb.createFont();

                if(type != null){
                    if(type.equals("header")){
                        headerFont.setBold(true);
                        style.setFont(headerFont);
                    }
                    if(type.equals("instructions")){
                        sheet.addMergedRegion(new CellRangeAddress(rowNum,rowNum,0,6));
                        style.setWrapText(true);
                        row.setRowStyle(style);
                        int lines = 2;
                        int pos=0;
                        while ((pos = val.indexOf("\n", pos) + 1) != 0) {
                            lines++;
                        }
                        row.setHeight((short)(row.getHeight() * lines));
                    }
                    if(type.equals("Correct")){
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.GREEN.getIndex());
                        style.setFont(headerFont);
                    }
                    if(type.equals("Incorrect")){
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.RED.getIndex());
                        style.setFont(headerFont);
                    }
                }

                cell.setCellStyle(style);
                cell.setCellValue(val);
            }
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return sheet;
    }

    private XSSFSheet addRowToSheet(XSSFWorkbook wb, XSSFSheet sheet, LinkedHashMap<String, String> map, int rowNum, String type) {
        try{
            ArrayList<String> header = new ArrayList<String>(hashMapHeader);
            // If this is the old mapping scheme, don't make the CMO Sample ID include IGO ID.
            if(NewMappingScheme == 0){
                int indexHeader = header.indexOf("MANIFEST_SAMPLE_ID");
                header.set(indexHeader, "CMO_SAMPLE_ID");
            }
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum=0;
            for(String key : header) {
                if(map.get(key) == null ||  map.get(key).isEmpty()) {
                    if(exceptionList.contains(key)) {
                        map.put(key, "na");
                        if (key == "SPECIMEN_COLLECTION_YEAR") {
                            map.put(key,"000");
                        }
                    } else if (silentList.contains(key)){
                        map.put(key, "");
                    } else {
                        map.put(key, "#empty");
                    }
                }
               
                row.createCell(cellNum++).setCellValue((String) map.get(key));
            }
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return sheet;
    }

    private String findDesignFile (String assay) {
        if(ReqType == "impact" || ReqType == "exome"){
            File dir = new File(designFilePath + "/" + assay);
            if( dir.isDirectory()){
                if(ReqType == "impact"){
                    File berger = new File(dir.getAbsolutePath() + "/" + assay + "__DESIGN__LATEST.berger");
                    if (berger.isFile()){
                        try{ 
                            return berger.getCanonicalPath();
                        } catch(Throwable e){
                        }
                    }else if(ReqType == "impact"  && ! runAsExome){
                        print("[ERROR] Cannot find design file for assay " + assay );
                    }
                }
                return dir.toString();
            }
        }
        return "NA";
    }

    private void printMappingFile (Map<String, Set<String>> SamplesAndRuns, String requestID, File projDir, String baitVersion) {
        try{

        HashSet <String> runsWithMultipleFolders = new HashSet<String>();
        
        String mappingFileContents = "";
        for(String sample: new ArrayList<String>(SamplesAndRuns.keySet())) {
            Set<String> runIDs = SamplesAndRuns.get(sample);
            ArrayList<String> path = new ArrayList<String>();
            HashSet<String> sample_pattern = new HashSet<String>();

            if(sampleAlias.keySet().contains(sample)){
                ArrayList<String> aliasSampleNames = new ArrayList<String>(Arrays.asList(sampleAlias.get(sample).split(";")));
                for(String aliasName : aliasSampleNames){
                    print("[WARNING] Sample " + sample + " has alias " + aliasName);
                    sample_pattern.add(aliasName.replaceAll("[_-]", "[-_]"));
                }
            } else {
                sample_pattern.add(sample.replaceAll("[_-]", "[-_]"));
            }

            // Here Find the RUN ID. Iterate through each directory in fastq_path so I can search through each FASTQ directory
            // Search each /FASTQ/ directory for directories that start with "RUN_ID"
            // Take the newest one?
            
            // This takes the best guess for the run id, and has bash fill out the missing parts!
            for(String id: runIDs){
                final String id2 = id;
                String RunIDFull = "";

                //Iterate through fastq_path 
                File dir = new File(fastq_path + "/hiseq/FASTQ/");
                
                File [] files = dir.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name){
                        return name.startsWith(id2);
                    }});

                // find out how many run IDs came back
                if(files == null){
                    print("[WARNING] No files");
                    continue;
                }
                if(files.length == 0){
                    print("[ERROR] Could not find sequencing run folder for Run ID: " + id);
                    mappingIssue=true;
                    return; 
                } else if (files.length > 1) {
                    // Here I will remove any directories that do NOT have the project as a folder in the directory.
                    ArrayList<File> runsWithProjectDir = new ArrayList<File>();
                    for(File runDir: files){
                        File requestPath = new File(runDir.getAbsoluteFile().toString() + "/Project_" + requestID);
                        //print("TESTING this path: " + requestPath.toString());
                        if(requestPath.exists() && requestPath.isDirectory()){
                            runsWithProjectDir.add(runDir);
                        }
                    }
                    files = runsWithProjectDir.toArray(new File[runsWithProjectDir.size()]);
                    if(files == null){
                        print("[WARNING] No files");
                        continue;
                    }
                    if(files.length == 0){
                        print("[ERROR] Could not find sequencing run folder that also contains request " + requestID + " for Run ID: " + id);
                        mappingIssue=true;
                        return; 
                    } else if (files.length > 1) {
                        Arrays.sort(files);
                        String foundFiles = StringUtils.join(files, ", ");
                        if(! runsWithMultipleFolders.contains(id)){
                            print("[WARNING] More than one sequencing run folder found for Run ID " + id + ": " +  foundFiles + " I will be picking the newest folder.");
                            runsWithMultipleFolders.add(id); 
                        }
                        RunIDFull = files[files.length -1].getAbsoluteFile().getName().toString();
                    } else {
                        RunIDFull = files[0].getAbsoluteFile().getName().toString();
                    }
                }  else {
                    RunIDFull = files[0].getAbsoluteFile().getName().toString();
                }
                
                // Grab RUN ID, save it for the request file.
                runIDlist.add(RunIDFull); 

                for( String S_Pattern : sample_pattern){
                    String pattern = "";
                    if(! sample.equals("FFPEPOOLEDNORMAL") && ! sample.equals("FROZENPOOLEDNORMAL") && ! sample.equals("MOUSEPOOLEDNORMAL")){
                        pattern = dir.toString() + "/" + RunIDFull + "*/Proj*" + requestID.replaceFirst("^0+(?!$)", "") + "/Sample_" + S_Pattern;
                    } else {
                        pattern = dir.toString() + "/" + RunIDFull + "*/Proj*" + "/Sample_" + S_Pattern + "*";
                    }
                
                    String cmd = "ls -d " + pattern;
 
                    //String cmd = "readlink -nv -f " + pattern;
                    Process pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                    pr.waitFor();
  
                    int exit = pr.exitValue();
                    if(exit != 0) {
                        String igoID =  SampleListToOutput.get(sample).get("IGO_ID");
                        String seqID = SampleListToOutput.get(sample).get("SEQ_IGO_ID");
                         
                        cmd = "ls -d " + pattern + "_IGO_" + seqID + "*";
  
                        pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                        pr.waitFor();                    
                        exit = pr.exitValue();

                        if(exit != 0){ 
                            cmd = "ls -d " + pattern + "_IGO_*";
                
                            pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                            pr.waitFor();                    
                            exit = pr.exitValue();
                
                            if(exit != 0){
 
                                print("[ERROR] Error while trying to find fastq Directory for " + sample + " it is probably mispelled, or has an alias.");
                                BufferedReader bufE = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                                while(bufE.ready()) {
                                    System.err.println("[ERROR] " + bufE.readLine());
                                }
                                mappingIssue=true;
                                continue;
                            }else{
                                NewMappingScheme =1;
                            }
                        } else {
                            // this working means that I have to change the cmo sample id to have the seq iD.
                            if(! seqID.equals(igoID)){
                                String manifestSampleID = SampleListToOutput.get(sample).get("MANIFEST_SAMPLE_ID").replace("IGO_" + igoID, "IGO_" + seqID);
                                SampleListToOutput.get(sample).put("MANIFEST_SAMPLE_ID", manifestSampleID);
                            }  
                            NewMappingScheme = 1;                        
                        }
                    } 
                    String sampleFqPath = StringUtils.chomp(IOUtils.toString(pr.getInputStream()));
                
                    if(sampleFqPath.isEmpty()){
                        continue;
                    } 

                    String[] paths = sampleFqPath.split("\n");

                    // Find out if this is single end or paired end by looking inside the directory for a R2_001.fastq.gz
                    for (String p :paths){
                        if((sample.contains("POOLEDNORMAL") && (sample.contains("FFPE") || sample.contains("FROZEN"))) && ! p.matches("(.*)IGO_" + baitVersion.toUpperCase() + "_[ATCG](.*)")){
                            continue;
                        }

                        String paired = "SE";
                        File sampleFq = new File(p);
                        File[] listOfFiles = sampleFq.listFiles(); 
                        for(File f1:listOfFiles){
                            if (f1.getName().endsWith("_R2_001.fastq.gz")){
                                paired = "PE";
                                break;
                            }
                        }
 
                       // Confirm there is a SampleSheet.csv in the path:
                       File samp_sheet = new File(p + "/SampleSheet.csv");
                       if (! samp_sheet.isFile() && ReqType.equals("impact")){
                           if(shiny){
                               System.out.print("[ERROR] ");
                           } else {
                               System.out.print("[WARNING] ");
                           }
                           print("Sample " + sample + " from run " + RunIDFull + " does not have a sample sheet in the sample directory. This will not pass the validator.");
                       }
                    

                       // FLATTEN ALL sample ids herei:
                        String sampleName = sampleNormalization(sample);
                        if(sampleSwap.size() > 0 && sampleSwap.keySet().contains(sample)){
                            sampleName = sampleNormalization(sampleSwap.get(sample)); 
                        }
                        if(sampleRenames.size() > 0 && sampleRenames.keySet().contains(sample)){
                            sampleName = sampleNormalization(sampleRenames.get(sample));
                        }

                        mappingFileContents +=  "_1\t" + sampleName + "\t" + RunIDFull + "\t" + p + "\t" + paired + "\n";

                    }
                }
            }
        }

        if(mappingFileContents.length() > 0 ){
            mappingFileContents = filterToAscii(mappingFileContents);
            File mappingFile = new File(projDir.getAbsolutePath() + "/Proj_" + requestID + "_sample_mapping.txt");
            PrintWriter pW = new PrintWriter(new FileWriter(mappingFile, false), false);
            filesCreated.add(mappingFile);
            pW.write(mappingFileContents);
            pW.close(); 
        }

        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }

    }
    
    private void printPairingFile (LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput, DataRecordManager drm, User apiUser, String pairing_filename, LinkedHashMap<String, Set<String>> patientSampleMap){
        try{
            LinkedHashMap<String, String> igo_tumor_info = new LinkedHashMap<String, String>();
            LinkedHashMap<String, String> pair_Info = new LinkedHashMap<String, String>();
            String warnings = "";
            ArrayList<String> exome_normal_list = new ArrayList<String>();

            HashSet<String> CorrectedCMOids = new HashSet<String>();

            // Make a list of IGO ids to give to printPairingFile script
            ArrayList<String> igoIDs = new ArrayList<String>();
            for(String samp : SampleListToOutput.keySet() ){
                igoIDs.add(SampleListToOutput.get(samp).get("IGO_ID"));
                igo_tumor_info.put(SampleListToOutput.get(samp).get("IGO_ID"), samp);
                CorrectedCMOids.add(SampleListToOutput.get(samp).get("CORRECTED_CMO_ID"));
            }
            
            // Go through each igo id and try and find a SamplePairing data record,
            for( String id: igoIDs){
                String cmoID = igo_tumor_info.get(id);
                List<DataRecord> records = drm.queryDataRecords("SamplePairing", "SampleId = '" + id + "'", apiUser);
                if (records.size() == 0 ){
                    if(! id.startsWith("CTRL-")){
                        warnings += "[WARNING] No pairing record for igo ID '" + id + "'\n";
                    }
                    continue;
                }
                 
                // If Sample Class doesn't have "Normal" in it, sample is tumor
                // Then save as Normal,Tumor
                DataRecord rec=records.get(records.size() - 1 );
                if(! SampleListToOutput.get(cmoID).get("SAMPLE_CLASS").contains("Normal")){
                    String tum = SampleListToOutput.get(igo_tumor_info.get(id)).get("CORRECTED_CMO_ID");
                    String norm = rec.getStringVal("SamplePair", apiUser);

                    if(norm.isEmpty()){
                        norm = "na";
                    }

                    norm=norm.replaceAll("\\s","");
                    if((! CorrectedCMOids.contains(norm)) && (!force) && (! norm.equals("na"))){
                        System.err.println("[WARNING] Normal matching with this tumor is NOT a valid sample in this request: Tumor: " + tum + " Normal: " + norm + " The normal will be changed to na.");
                        norm="na";
                    }
                    if(pair_Info.containsKey(tum) && pair_Info.get(tum) != norm){
                        print("[ERROR] Tumor is matched with two different normals. I have no idea how this happened! Tumor: " + tum + " Normal: " + norm);
                        
                        System.out.print(warnings);
                        return;
                    }
                    pair_Info.put(tum, norm);
                } else if (ReqType.equals("exome")) {
                    // Add normals to a list, check at the end that all normals are in the pairing file. Otherwise add them with na.
                    exome_normal_list.add(rec.getStringVal("OtherSampleId", apiUser));
                }
            }
            for(String NormIds : exome_normal_list){
                if (! pair_Info.containsKey(NormIds) && ! pair_Info.containsValue(NormIds)){
                    pair_Info.put(NormIds, "na");
                } 
            }
            
            if(pair_Info.size() > 0 ) {
                System.out.print(warnings);
            }else if (!patientSampleMap.isEmpty()){
                // There were no pairing sample records, do smart pairing
                // For each patient ID, separate samples by normal and tumor. Then if there is at least 1 normal, match it with all the tumors.
                // TODO: This will have to become populated into the LIMs Perhaps send a signal to another script that will suck it into the LIMs.

                print("[INFO] Pairing records not found, trying smart Pairing");
                pair_Info = smartPairing(SampleListToOutput, patientSampleMap);
                
            } else{
                print("[WARNING] No Pairing File will be output.");
            }
            
            //Done going through all igo ids, now print
            if(pair_Info.size() > 0){
                File pairing_file = new File(pairing_filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                filesCreated.add(pairing_file);
                for( String tum : pair_Info.keySet()){
                    String norm = pair_Info.get(tum);
                    if(ReqType == "exome" && exome_normal_list.contains(tum)){
                        norm= tum;
                        tum = "na";
                    }
                    //if(norm.equals("na") && // HERE ADD A THING THAT CHECKS OUT NormalPool SAMPLES){
                    //    norm = pickPooledNormal(SampleListToOutput, tum, // normal pool samples);
                    //}
                    pW.write(sampleNormalization(norm) + "\t" + sampleNormalization(tum) + "\n");
                }
                pW.close();

                if(shiny || krista){
                    printPairingExcel(pairing_filename, pair_Info, exome_normal_list);
                }
            }
            
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
    }
    
    private LinkedHashMap<String, String> smartPairing(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput, LinkedHashMap<String, Set<String>> patientSampleMap) {
        // for each patient sample, go through the set of things and find out if they are tumor or normal.
        // for each tumor, match with the first normal.
        // no normal? put na
        // no tumor? don't add to pair_Info
        LinkedHashMap<String, String> pair_Info = new LinkedHashMap<String, String>();
        for(String patient : patientSampleMap.keySet() ){
            ArrayList<String> tumors = new ArrayList<String>();
            ArrayList<String> allNormals = new ArrayList<String>();
            // populating the tumors and normals for this patient
            for(String s : patientSampleMap.get(patient)){
                LinkedHashMap<String, String> samp =SampleListToOutput.get(s);
                if (samp.get("SAMPLE_CLASS").contains("Normal")){
                    allNormals.add(s);
                } else {
                    tumors.add(s);
                }
            }
             
            //go through each tumor, add it to the pair_Info with a normal if possible
            for(String t : tumors){
                LinkedHashMap<String, String> tum = SampleListToOutput.get(t);
                String corrected_t = tum.get("CORRECTED_CMO_ID");
                String PRESERVATION = tum.get("SPECIMEN_PRESERVATION_TYPE");
                String SITE = tum.get("TISSUE_SITE");
                //print("Tumor: " + t + " Preservation: " + PRESERVATION + " Site: " + SITE);
                if (allNormals.size() > 0){
                    //print("Number or normals to choose from: " + String.valueOf(allNormals.size()));
                    ArrayList<String> normals = new ArrayList<String>(allNormals);
                    ArrayList<String> preservationNormals = new ArrayList<String>();
                    ArrayList<String> useTheseNormals = new ArrayList<String>();
                    // cycle through and find out if you have normal with same tumor sample preservation type
                    for( String n : allNormals){
                        LinkedHashMap<String, String> norm = SampleListToOutput.get(n);
                        if(norm.get("SPECIMEN_PRESERVATION_TYPE").equals(PRESERVATION)){
                            preservationNormals.add(n);
                        }
                    }
                    // Now if any match preservation type, use those normals to continue matching
                    if(preservationNormals.size() > 0){
                        //print("Number that matched preservation: " + String.valueOf(preservationNormals.size()));
                        normals = new ArrayList<String>(preservationNormals);
                        //print("New Normals to choose from " + normals);
                    }
                    // go through and see if any of the normals have the same tissue site. 
                    for (String n : normals) {
                        LinkedHashMap<String, String> norm = SampleListToOutput.get(n);
                        if(norm.get("TISSUE_SITE").equals(SITE)){
                            useTheseNormals.add(n);
                        }
                    }
                    // If there are more than one, just pick the first.
                    String n = "";
                    if(useTheseNormals.size() > 0){
                        n = useTheseNormals.get(0);
                    } else{
                        n = normals.get(0);
                    }
                    LinkedHashMap<String, String> norm = SampleListToOutput.get(n);
                    pair_Info.put(corrected_t, norm.get("CORRECTED_CMO_ID"));
                }else {
                    // no normal, for now put NA
                    pair_Info.put(corrected_t, "na");
                }
            }
        }
        
        return pair_Info;
        
    }
    
    private String patientNormalization(String sample){
        sample = sample.replace("-","_");
        if (! sample.equals("na")){
        sample = "p_" + sample;
        }
        return sample;
    }

    private String sampleNormalization(String sample){
        sample = sample.replace("-","_");
        if (! sample.equals("na")){
        sample = "s_" + sample;
        }
        return sample;
    }

    private LinkedHashMap<String, Set<String>>  createPatientSampleMapping(LinkedHashMap<String, LinkedHashMap<String, String>> SampleList){
        LinkedHashMap<String, Set<String>> tempMap = new LinkedHashMap<String, Set<String>>();
        for(String sid : SampleList.keySet()){
            LinkedHashMap<String, String> rec = SampleList.get(sid);
            String pid = rec.get("CMO_PATIENT_ID");
            String s = rec.get("IGO_ID");
            //print("CMO_PATIENT_ID: " + pid + " SAMPLE: " + s + " SID: " + sid);
            if(pid.startsWith("#")){
                print("[WARNING] Cannot make smart mapping because Patient ID is emtpy or has an issue: " + pid);
                return new LinkedHashMap<String, Set<String>>();
            }
            if(tempMap.containsKey(pid)){
                Set<String> tempSet = tempMap.get(pid);
                tempSet.add(sid);
                tempMap.put(pid, tempSet);
            } else {
                Set<String> tempSet = new HashSet<String>();
                tempSet.add(sid);
                tempMap.put(pid, tempSet);
            }
        }
    
        return tempMap;
    }

    private void printFileType(ArrayList<LinkedHashMap<String,String>> sampInfo, String outputFilename, String fileType){
        String outputText = "";
        String manualHeader="";

        if(fileType=="data_clinical"){
            manualHeader=manualClinicalHeader;
        } else if (fileType=="patient"){
            manualHeader=manualMappingPatientHeader;
        }
        
        // Create map for maping
        LinkedHashMap<String, String> fieldMapping = new LinkedHashMap<String, String>();
        ArrayList <String> fieldList = new ArrayList<String>(Arrays.asList(manualHeader.split(",")));
        for(String fields: fieldList){
            String[] parts = fields.split(":");
            fieldMapping.put(parts[0], parts[1]);
        }

        // add header
        outputText += StringUtils.join(fieldMapping.keySet(), "\t");
        outputText += "\n";
        outputText = filterToAscii(outputText);

        for (LinkedHashMap<String,String> samp : sampInfo){
            ArrayList<String> line = new ArrayList<String>();
            for (String key : fieldMapping.values()){
                String val = samp.get(key);

                if(key.equals("CMO_SAMPLE_ID") ||  key.equals("INVESTIGATOR_SAMPLE_ID")) {
                    val = sampleNormalization(val);
                }
                if(key.equals("CMO_PATIENT_ID") ||  key.equals("INVESTIGATOR_PATIENT_ID")) {
                    val = patientNormalization(val);
                }
                if(key.equals("SAMPLE_CLASS") && fileType.equals("patient")){
                    if (! val.contains("Normal")){
                        val = "Tumor";
                    }
                }
                line.add(val);
            }
            outputText += StringUtils.join(line, "\t");
            outputText += "\n";
        }

        if(outputText.length() > 0){
            try{
                outputText = filterToAscii(outputText);
                File outputFile = new File(outputFilename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                filesCreated.add(outputFile);
                pW.write(outputText);
                pW.close();
            } catch (Throwable e ) {
                logger.log(Level.SEVERE, "Exception thrown: ", e);
                e.printStackTrace(console);
            }
        }
        return;
    }

    private void printGroupingFile(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput, LinkedHashMap<String, Set<String>> patientSampleMap, String outputFilename){
        String outputText = "";
        DecimalFormat df = new DecimalFormat("000");
        int count = 0;
        if(patientSampleMap.isEmpty()){
            print("[WARNING] No patient sample map, therefore no grouping file created.");
            return;
        }
        for (String k : patientSampleMap.keySet()){
            Set<String> tempSet = patientSampleMap.get(k);
            for (String i : tempSet){
                LinkedHashMap<String, String> sampInfo = SampleListToOutput.get(i);
                outputText += sampleNormalization(sampInfo.get("CORRECTED_CMO_ID")) + "\tGroup_" + df.format(count) + "\n";
            }
            count++;
        }
        if(outputText.length() > 0){
            try{
                outputText = filterToAscii(outputText);
                File outputFile = new File(outputFilename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                filesCreated.add(outputFile);
                pW.write(outputText);
                pW.close();
            } catch (Throwable e ) {
                logger.log(Level.SEVERE, "Exception thrown: ", e);
                e.printStackTrace(console);
            }
        }
        return; 
    }

    private void printRequestFile(ArrayList<String> pInfo, String reqSpecies, String requestID, File projDir){
        Set<String> placeholder = new HashSet<String>();
        printRequestFile(pInfo, placeholder, placeholder, placeholder, requestID, projDir);
        return;
    }

    private void printRequestFile(ArrayList<String> pInfo, Set<String> ampType, Set<String> libType, Set<String> strand, String requestID, File projDir){
        // This will change the fields of the pInfo array, and print out the correct field
        // It will also pr/int all the ampType and libTypes, and species.
        
        String requestFileContents = "";

        //put the request Type
        if (ReqType.equals("exome")){
            requestFileContents += "Pipelines: variants\n";
            requestFileContents += "Run_Pipeline: variants\n";
        }else if (ReqType.equals("impact")) {
            requestFileContents += "Pipelines: dmp\n";
            requestFileContents += "Run_Pipeline: dmp\n";
        }else if (ReqType.equals("rnaseq")){
            requestFileContents += "Run_Pipeline: rnaseq\n";
        }else if (recipe.toLowerCase().equals("chipseq")){
            requestFileContents += "Run_Pipeline: chipseq\n";
        }else{
            requestFileContents += "Run_Pipeline: other\n";
        }

        // This is quickly generating a map from old name to new name (validator takes old name)
        Map<String, String> convertFieldNames = new LinkedHashMap<String, String>();
        for(String conv : manualMappingPinfoToRequestFile.split(",")){
            String [] parts = conv.split(":", 2);
            convertFieldNames.put(parts[0], parts[1]);
        }
        // Now go through pInfo, and swap out the old name for the new name, and print
        for(String line : pInfo){
            String[] splitLine = line.split(": ", 2);
            if(splitLine.length == 1){
                continue;
                //splitLine = new String[] {splitLine[0], "NA"}; 
            } else if ( splitLine[1].isEmpty() || splitLine[1] == "#EMPTY" ){
                splitLine[1] = "NA";
            }

            if(convertFieldNames.containsKey(splitLine[0])){
                if(splitLine[0].endsWith("_E-mail")){
                    if(splitLine[0].equals("Requestor_E-mail")){
                        requestFileContents += "Investigator_E-mail: " + splitLine[1] + "\n";
                    }
                    if(splitLine[0].equals("Lab_Head_E-mail")){
                        requestFileContents += "PI_E-mail: " + splitLine[1] + "\n";
                    }
                    String[] temp = splitLine[1].split("@");
                    splitLine[1] = temp[0];
                }
                requestFileContents += convertFieldNames.get(splitLine[0]) + ": " + splitLine[1] + "\n";
            }else if(splitLine[0].contains("IGO_Project_ID") || splitLine[0].equals("ProjectID")){
                requestFileContents += "ProjectID: Proj_" + splitLine[1] + "\n";
            }else if(splitLine[0].contains("Platform") || splitLine[0].equals("Readme_Info") || splitLine[0].equals("Sample_Type") || splitLine[0].equals("Bioinformatic_Request")  ){
                    continue;
            }else if (splitLine[0].equals("Project_Manager")){
                String[] tempName = splitLine[1].split(", ");
                if(tempName.length > 1){
                    requestFileContents += splitLine[0] + ": " + tempName[1] + " " + tempName[0] + "\n";
                } else {
                    requestFileContents += splitLine[0] + ": " + splitLine[1] + "\n";
                }
            }else{
                requestFileContents += line + "\n";
            }
            if(splitLine[0].equals("Requestor_E-mail")){
                invest = splitLine[1];
            }
            if (splitLine[0].equals("Lab_Head_E-mail")){
                pi = splitLine[1];
            }
        }

        if(invest.isEmpty() || pi.isEmpty()){
            print("[ERROR] Cannot create run number because PI and/or Investigator is missing. " + pi + " " + invest);
        }else{
            if(runNum != 0){
                requestFileContents += "RunNumber: " + runNum + "\n";
            }
        }
        if ( runNum > 1){
            if(! shiny && (rerunReason == null || rerunReason.isEmpty()) ){
                print("[ERROR] This generation of the project files is a rerun, but no rerun reason was given. Use option 'rerunReason' to give a reason for this rerun in quotes. ");
                exitLater=true;
                return;
            }
            requestFileContents += "Reason_for_rerun: " + rerunReason + "\n";

        }
        requestFileContents += "RunID: " + StringUtils.join(runIDlist, ", ") + "\n";

        requestFileContents += "Institution: cmo\n";

        if(ReqType == "other"){
            requestFileContents += "Recipe: " + recipe + "\n";
        }
 
        if(ReqType == "rnaseq"){
            //Now print the rest of the information
            String [] ampTypes = ampType.toArray(new String[0]);
            requestFileContents += "AmplificationTypes: " + StringUtils.join(ampTypes, ", ") + "\n";

            String [] libTypes = libType.toArray(new String[0]);
            requestFileContents += "LibraryTypes: " + StringUtils.join(libTypes, ", ") + "\n";

            if(strand.size() > 1){
                print("[WARNING] Multiple strandedness options found!");
            }

            String [] strandString = strand.toArray(new String[0]);
            requestFileContents += "Strand: " + StringUtils.join(strandString, ", ") + "\n";
            requestFileContents += "Pipelines: ";
            // If pipeline_options (command line) is not empty, put here. remember to remove the underscores
            // For now I am under the assumption that this is being passed correctly.
            if(pipeline_options != null && pipeline_options.length > 0){
                String pipelines = StringUtils.join(pipeline_options, ", ").replace("_", " ");
                requestFileContents +=pipelines;
            } else {
                // Default is Alignment STAR, Gene Count, Differential Gene Expression
                requestFileContents += "NULL, RNASEQ_STANDARD_GENE_V1, RNASEQ_DIFFERENTIAL_GENE_V1";
            }
            requestFileContents += "\n";
        }else {
            requestFileContents += "AmplificationTypes: NA\n";
            requestFileContents += "LibraryTypes: NA\n";
            requestFileContents += "Strand: NA\n";
        }

        
        // adding projectFolder back
        if(ReqType.equals("impact") || ReqType.equals("exome")){
            requestFileContents += "ProjectFolder: " + String.valueOf(projDir).replaceAll("BIC/drafts", "CMO") + "\n" ;
        }else {
            requestFileContents += "ProjectFolder: " + String.valueOf(projDir).replaceAll("drafts", ReqType) + "\n" ;
        }
        

        // Date of last update
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        requestFileContents += "DateOfLastUpdate: " + dateFormat.format(date) + "\n";

        if(!noPortal && ! ReqType.equals("rnaseq")){
            printPortalConfig(projDir, requestFileContents, requestID); 
        }

        try{
            requestFileContents = filterToAscii(requestFileContents);
            File requestFile = new File(projDir.getAbsolutePath() + "/Proj_" + requestID + "_request.txt");
            PrintWriter pW = new PrintWriter(new FileWriter(requestFile, false), false);
            filesCreated.add(requestFile);
            pW.write(requestFileContents);
            pW.close();
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        
    }

    private void printPortalConfig(File projDir, String requestFileContents, String requestID){
        // First make map from request to portal config
	// THen create final map of portal config with values. 
        Map<String, String> configRequestMap = new LinkedHashMap<String, String>();
        for (String conv: manualMappingConfigMap.split(",")){
            String [] parts = conv.split(":", 2);
            configRequestMap.put(parts[1], parts[0]);
        }
        String groups = "COMPONC;";
        String assay = "";
        String dataClinicalPath = "";

        String replaceText = ReqType;
        if(replaceText.equals("exome")){
            replaceText = "variant";
        }
        // For each line of requestFileContents, grab any fields that are in the map
        // and add them to the configFileContents variable.
        String configFileContents = "";
        for (String line : requestFileContents.split("\n")){
            String[] parts = line.split(": ", 2);
            if(configRequestMap.containsKey(parts[0])){
                if(parts[0].equals("PI")){
                    groups += parts[1].toUpperCase();
                }
                if(parts[0].equals("Assay")){
                    if(ReqType == "impact"){
                        assay = parts[1].toUpperCase();
                    } else {
                        assay = parts[1];
                    }
                }
                configFileContents += configRequestMap.get(parts[0]) + "=\"" + parts[1] + "\"\n";
            }

            // Change path depending on where it should be going 
            if(ReqType.equals("impact") || ReqType.equals("exome")){
                dataClinicalPath = String.valueOf(projDir).replaceAll("BIC/drafts", "CMO") + "\n" ;
            }else {
                dataClinicalPath =  String.valueOf(projDir).replaceAll("drafts", replaceText) + "\n" ;
            }
        }
        // Now add all the fields that can't be grabbed from the request file
        configFileContents += "project=\"" + requestID + "\"\n";
        configFileContents += "groups=\"" + groups + "\"\n";
        configFileContents += "cna_seg=\"\"\n";
        configFileContents += "cna_seg_desc=\"Somatic CNA data (copy number ratio from tumor samples minus ratio from matched normals).\"\n";
        configFileContents += "cna=\"\"\n";
        configFileContents += "maf=\"\"\n";
        configFileContents += "inst=\"cmo\"\n";
        configFileContents += "maf_desc=\"" + assay + " sequencing of tumor/normal samples\"\n";
        if (! dataClinicalPath.isEmpty()){
            configFileContents += "data_clinical=\"" + dataClinicalPath + "/Proj_" + requestID + "_sample_data_clinical.txt\"\n";
        } else {
            print("[WARNING] Cannot find path to data clinical file. Not included in portal config.");
            configFileContents += "data_clinical=\"\"\n";
        }

        try{
            configFileContents = filterToAscii(configFileContents);
            File configFile = new File(projDir.getAbsolutePath() + "/Proj_" + requestID + "_portal_conf.txt");
            filesCreated.add(configFile);
            PrintWriter pW = new PrintWriter(new FileWriter(configFile, false), false);
            pW.write(configFileContents);
            pW.close();
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }

        
    }

    private int getRunNumber(String requestID, String pi, String invest){
        final String req = requestID;
        File resultDir = new File(resultsPathPrefix + "/" + pi + "/" + invest);

        //print("resultDir: " + String.valueOf(resultDir));
        File [] files = resultDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name){
                return name.endsWith(req.replaceFirst("^0+(?!$)", ""));
            }});
        if(files != null && files.length == 1){
            File projectResultDir = files[0];
            File [] files2 = projectResultDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name){
                    return name.startsWith("r_");
                }});
            if(files2.length > 0){
                int[] runs = new int[files2.length];
                int index = 0;
                for(File f : files2){
                    int pastRuns = Integer.parseInt(f.getName().replaceFirst("^r_", ""));
                    runs[index] = pastRuns;
                    index++;
                }

                Arrays.sort(runs);
                int nextRun = runs[runs.length - 1] + 1;

                return nextRun;
            }
        }
        print("[WARNING] Could not determine PIPELINE RUN NUMBER from delivery directory. Setting to: 1. If this is incorrect, email cmo-project-start@cbio.mskcc.org");

        return 1;

    }

    private void print(String message){
        // Log and print to stdout
        if(message.startsWith("[")){
            if(message.startsWith("[ERROR]")){
                exitLater = true;
            }
            log_messages.add(message);
        }
        System.out.println(message);
    } 

    private void printToPipelineRunLog(String requestID){
        boolean newFile = false;
        // If this project already has a pipeline run log add rerun information to it.
        // IF the rerun number has already been marked in there, just add to it....
        // Create file is not created before:
        File archiveProject = new File(archivePath + "/Proj_" + requestID);

        if(! archiveProject.exists()){
            print("Making archive directory, it should be made already");
            archiveProject.mkdirs();
        }

        File runLogFile = new File(archiveProject + "/Proj_" + requestID + "_runs.log");
        if(! runLogFile.exists()){
            newFile = true;
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        String reason = "NA";
        if(rerunReason != null && ! rerunReason.isEmpty()){
            reason = rerunReason;
        }

        try{
            PrintWriter pw = new PrintWriter(new FileWriter(runLogFile, true), false);

            if (newFile){
                pw.write("Date\tTime\tRun_Number\tReason_For_Rerun\n");
            }

            pw.println(dateFormat.format(now) + "\t" + timeFormat.format(now) + "\t" + runNum + "\t" + reason); 

            pw.close();    
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
        return;
    }

    private void printLogMessages(File logFile){
        try{
            PrintWriter pw = new PrintWriter(new FileWriter(logFile, false), false);
            for ( String line : log_messages){
                pw.write(line + "\n");
            }
            pw.close();
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }
    }
/*



 This is where all functions that happen after printing project files. 



*/
  
    private void copyToArchive(String fromPath, String requestID, String dateDir){
        copyToArchive(fromPath, requestID, dateDir, "");
    }

    private void copyToArchive(String fromPath, String requestID, String dateDir, String suffix){
       File curDir = new File(fromPath);
       File projDir = new File(archivePath + "/Proj_" + requestID + "/" + dateDir);
       
       try{
          if( curDir.exists() && curDir.isDirectory() && curDir.listFiles().length > 0){
              if (! projDir.exists()){
                  projDir.mkdirs();
              }
              File[] projFiles = curDir.listFiles();
              for(File f : projFiles){
                  File to = new File(projDir + "/" + f.getName() + suffix);
                  if(f.isDirectory()){
                      FileUtils.copyDirectory(f, to);            
                      continue;
                  }
                  Files.copy(f.toPath(), to.toPath(), REPLACE_EXISTING);
                  setPermissions(f);
              }
          } else {
              print("[ERROR] Cannot copy project files to archive directory, the current directory is not valid or has no files.");
          } 

       } catch (Throwable e){
           e.printStackTrace(console);
       }
    }
 
    private void printReadmeFile(String fileText, String requestID, File projDir){
        if(fileText.length() > 0){
        
        try{
            fileText = filterToAscii(fileText);
            File readmeFile = new File(projDir.getAbsolutePath() + "/Proj_" + requestID + "_README.txt");
            PrintWriter pW = new PrintWriter(new FileWriter(readmeFile, false), false);
            filesCreated.add(readmeFile);
            pW.write(fileText + "\n");
            pW.close();
        } catch (Throwable e ) {
            logger.log(Level.SEVERE, "Exception thrown: ", e);
            e.printStackTrace(console);
        }

        }

        return ;
    }
    
    private Map<String, String> linkPoolToRunID(Map<String, String> RunID_and_PoolName, String RunID, String poolName) {
        if (RunID_and_PoolName.containsKey(RunID)) {
            RunID_and_PoolName.put(RunID, RunID_and_PoolName.get(RunID) + ";" + poolName);
        } else {
            RunID_and_PoolName.put(RunID, poolName);
        }

        return RunID_and_PoolName;
    }

    public String filterToAscii(String highUnicode){
        String lettersAdded = highUnicode.replaceAll("ß", "ss").replaceAll("æ", "ae").replaceAll("Æ", "Ae");
        return Normalizer.normalize(lettersAdded, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }
    
    /*

    NESTED CLASS HERE *******************************

    This class will shut down the server if someone hits ctrl-c 
    I guess I do this enough to need it.

    */

    public static class MySafeShutdown extends Thread {
        @Override
        public void run(){
            CreateManifestSheet.closeConnection();

            return;
        }
    }
}


