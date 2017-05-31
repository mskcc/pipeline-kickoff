package org.mskcc.kickoff.lims;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.datarecord.NotFound;
import com.velox.api.user.User;
import com.velox.api.util.ServerException;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import com.velox.util.LogWriter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.mskcc.kickoff.PriorityAwareLogMessage;
import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.ArgumentsFileReporter;
import org.mskcc.kickoff.config.LogConfigurer;
import org.mskcc.kickoff.domain.LibType;
import org.mskcc.kickoff.domain.Recipe;
import org.mskcc.kickoff.domain.RequestSpecies;
import org.mskcc.kickoff.domain.Strand;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.velox.util.VeloxConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.io.*;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.mskcc.kickoff.config.Arguments.*;

/**
 * This class is an example standalone program.
 *
 * @author Krista Kaz (most of the framing of this script was copied from Aaron and Dmitri's examples/scripts)
 */
@ComponentScan(basePackages = "org.mskcc.kickoff")
class CreateManifestSheet {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger pmLogger = Logger.getLogger(Constants.PM_LOGGER);
    private static final HashSet<String> runIDlist = new HashSet<>();
    private static int NewMappingScheme = 0;
    private static VeloxConnection connection;
    private final Set<PosixFilePermission> DIRperms = new HashSet<>();
    private final Set<PosixFilePermission> FILEperms = new HashSet<>();
    private final String manualMappingPinfoToRequestFile = "Alternate_E-mails:DeliverTo,Lab_Head:PI_Name,Lab_Head_E-mail:PI,Requestor:Investigator_Name,Requestor_E-mail:Investigator,CMO_Project_ID:ProjectName,Final_Project_Title:ProjectTitle,CMO_Project_Brief:ProjectDesc";
    private final String manualMappingPatientHeader = "Pool:REQ_ID,Sample_ID:CMO_SAMPLE_ID,Collab_ID:INVESTIGATOR_SAMPLE_ID,Patient_ID:CMO_PATIENT_ID,Class:SAMPLE_CLASS,Sample_type:SPECIMEN_PRESERVATION_TYPE,Input_ng:LIBRARY_INPUT,Library_yield:LIBRARY_YIELD,Pool_input:CAPTURE_INPUT,Bait_version:BAIT_VERSION,Sex:SEX";
    private final String manualClinicalHeader = "SAMPLE_ID:CMO_SAMPLE_ID,PATIENT_ID:CMO_PATIENT_ID,COLLAB_ID:INVESTIGATOR_SAMPLE_ID,SAMPLE_TYPE:SAMPLE_TYPE,GENE_PANEL:BAIT_VERSION,ONCOTREE_CODE:ONCOTREE_CODE,SAMPLE_CLASS:SAMPLE_CLASS,SPECIMEN_PRESERVATION_TYPE:SPECIMEN_PRESERVATION_TYPE,SEX:SEX,TISSUE_SITE:TISSUE_SITE";
    private final List<String> hashMapHeader = Arrays.asList(Constants.MANIFEST_SAMPLE_ID, Constants.CMO_PATIENT_ID, Constants.INVESTIGATOR_SAMPLE_ID, Constants.INVESTIGATOR_PATIENT_ID, Constants.ONCOTREE_CODE, Constants.SAMPLE_CLASS, Constants.TISSUE_SITE, Constants.SAMPLE_TYPE, Constants.SPECIMEN_PRESERVATION_TYPE, Constants.Excel.SPECIMEN_COLLECTION_YEAR, "SEX", "BARCODE_ID", "BARCODE_INDEX", "LIBRARY_INPUT", "LIBRARY_YIELD", "CAPTURE_INPUT", "CAPTURE_NAME", "CAPTURE_CONCENTRATION", Constants.CAPTURE_BAIT_SET, Constants.SPIKE_IN_GENES, Constants.STATUS, Constants.INCLUDE_RUN_ID, Constants.EXCLUDE_RUN_ID);
    private final String manualMappingHashMap = "LIBRARY_INPUT:LIBRARY_INPUT[ng],LIBRARY_YIELD:LIBRARY_YIELD[ng],CAPTURE_INPUT:CAPTURE_INPUT[ng],CAPTURE_CONCENTRATION:CAPTURE_CONCENTRATION[nM],MANIFEST_SAMPLE_ID:CMO_SAMPLE_ID";
    private final String manualMappingConfigMap = "name:ProjectTitle,desc:ProjectDesc,invest:PI,invest_name:PI_Name,tumor_type:TumorType,date_of_last_update:DateOfLastUpdate,assay_type:Assay";
    private final HashSet<File> filesCreated = new HashSet<>();
    private final ArrayList<DataRecord> passingSeqRuns = new ArrayList<>();
    private final LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput = new LinkedHashMap<>();
    private final Set<String> sampleRuns = new HashSet<>();
    private final Map<String, Set<String>> badRuns = new HashMap<>();
    private final Set<String> poolRuns = new HashSet<>();
    private final Map<String, Integer> readsForSample = new HashMap<>();
    //This collects all library protocol types and Amplification protocol types
    private final Set<LibType> libTypes = new HashSet<>();
    private final Set<String> ampType = new HashSet<>();
    private final Set<Strand> strand = new HashSet<>();
    private final HashMap<String, String> sampleSwap = new HashMap<>();
    private final HashMap<String, String> sampleAlias = new HashMap<>();
    //This is exception list, these columns are OKAY if they are empty, (gets na) rather than #empty
    private final List<String> exceptionList = Arrays.asList(Constants.TISSUE_SITE, Constants.Excel.SPECIMEN_COLLECTION_YEAR, Constants.SPIKE_IN_GENES);
    private final List<String> silentList = Arrays.asList(Constants.STATUS, Constants.EXCLUDE_RUN_ID);
    @Value("${productionProjectFilePath}")
    private String productionProjectFilePath;
    @Value("${draftProjectFilePath}")
    private String draftProjectFilePath;
    @Value("${archivePath}")
    private String archivePath;
    @Value("${fastq_path}")
    private String fastq_path;
    @Value("${designFilePath}")
    private String designFilePath;
    @Value("${resultsPathPrefix}")
    private String resultsPathPrefix;
    @Value("${sampleKeyExamplesPath}")
    private String sampleKeyExamplesPath;
    @Value("${limsConnectionFilePath}")
    private String limsConnectionFilePath;
    private String extraReadmeInfo = "";
    private RequestSpecies requestSpecies;
    private Boolean force = false;
    private Boolean mappingIssue = false;
    private int runNum;
    private String baitVersion = Constants.EMPTY;
    private String pi;
    private String invest;
    private User user;
    private Map<String, String> RunID_and_PoolName = new LinkedHashMap<>();
    private LogWriter deadLog;
    private File outLogDir = null;
    private String ReqType = "";
    private List<Recipe> recipes;
    private List<PriorityAwareLogMessage> poolQCWarnings = new ArrayList<>();
    // This is for sample renames and sample swaps
    private HashMap<String, String> sampleRenames = new HashMap<>();

    @Autowired
    private ProjectNameValidator projectNameValidator;

    @Autowired
    private LogConfigurer logConfigurer;

    public static void main(String[] args) throws ServerException {
        try {
            parseArguments(args);
            ConfigurableApplicationContext context = configureSpringContext();
            CreateManifestSheet createManifestSheet = context.getBean(CreateManifestSheet.class);

            addShutdownHook();

            createManifestSheet.run();
        } catch (Exception e) {
            devLogger.error(String.format("Error while generating manifest files for project: %s", Arguments.project));
        }
    }

    private static void addShutdownHook() {
        MySafeShutdown sh = new MySafeShutdown();
        Runtime.getRuntime().addShutdownHook(sh);
    }

    private static ConfigurableApplicationContext configureSpringContext() {
        ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(CreateManifestSheet.class);
        context.getEnvironment().setDefaultProfiles(Constants.PROD_PROFILE, Constants.IGO_PROFILE);
        context.registerShutdownHook();
        return context;
    }

    private static void saveCurrentArgumentToFile() {
        ArgumentsFileReporter argumentsFileReporter = new ArgumentsFileReporter();
        argumentsFileReporter.printCurrentArgumentsToFile();
    }

    private static void closeConnection() {
        if (connection.isConnected()) {
            try {
                connection.close();
            } catch (Exception e) {
                devLogger.warn("Exception thrown while closing lims connection", e);
            }
        }
    }

    private void run() {
        logConfigurer.configureDevLog();
        devLogger.info("Received program arguments: " + toPrintable());
        saveCurrentArgumentToFile();
        projectNameValidator.validate(Arguments.project);

        generate();
    }

    /**
     * Connect to a server, then execute the rest of code.
     */
    private void generate() {
        connection = new VeloxConnection(limsConnectionFilePath);
        try {
            connection.openFromFile();

            if (connection.isConnected())
                user = connection.getUser();

            VeloxStandalone.run(connection, new VeloxTask<Object>() {
                @Override
                public Object performTask() throws VeloxStandaloneException {
                    queryProjectInfo(user, dataRecordManager, project);
                    return new Object();
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            closeConnection();
        }
    }

    private void queryProjectInfo(User apiUser, DataRecordManager drm, String requestID) {
        initializeFilePermissions();

        // this is to set up if production or draft (depreciated, everything goes to the draft project file path)
        Boolean draft = !prod;
        // Sets up project file path
        String projectFilePath = productionProjectFilePath + "/" + ReqType + "/" + Utils.getFullProjectNameWithPrefix(requestID);
        if (draft) {
            projectFilePath = draftProjectFilePath + "/" + Utils.getFullProjectNameWithPrefix(requestID);
        }

        try {
            List<DataRecord> requests = drm.queryDataRecords(VeloxConstants.REQUEST, "RequestId = '" + requestID + "'", apiUser);

            if (requests.size() == 0) {
                String message = String.format("No matching requests for request id: %s", Arguments.project);
                pmLogger.info(message);
                devLogger.error(message);
                return;
            }

            logConfigurer.configurePmLog();

            //checks argument outdir. If someone put this, it creates a directory for this project, and changes project file path to outdir
            if (outdir != null && !outdir.isEmpty()) {
                File f = new File(outdir);
                if (f.exists() && f.isDirectory()) {
                    outdir += "/" + Utils.getFullProjectNameWithPrefix(requestID);
                    File i = new File(outdir);
                    if (!i.exists()) {
                        i.mkdir();
                    }
                    log(String.format("Overwriting default dir to %s", outdir), Level.INFO, Level.INFO);
                    projectFilePath = outdir;
                } else {
                    logError(String.format("The outdir directory you gave me is empty or does not exist: %s", outdir), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                }
            }
            // create directory, create log dir
            File projDir = new File(projectFilePath);
            if (!projDir.exists()) {
                projDir.mkdir();
            }

            for (DataRecord request : requests) {
                // First check to see if I can autogen the files for this project:
                boolean manualDemux = false;
                boolean autoGenAble = false;

                try {
                    autoGenAble = request.getBooleanVal(Constants.BIC_AUTORUNNABLE, apiUser);
                    manualDemux = request.getBooleanVal(Constants.MANUAL_DEMUX, apiUser);
                } catch (NullPointerException ignored) {
                }
                if (!autoGenAble) {
                    // get reason why if there is one. Found in readme of request with field "NOT_AUTORUNNABLE"
                    String reason = "";
                    String[] bicReadmeLines = (request.getStringVal(Constants.READ_ME, apiUser)).split("\\n\\r|\\n");
                    for (String bicLine : bicReadmeLines) {
                        if (bicLine.startsWith(Constants.NOT_AUTORUNNABLE)) {
                            reason = "REASON: " + bicLine;
                            break;
                        }
                    }
                    logError(String.format("According to the LIMS, project %s cannot be run through this script. %s Sorry :(", requestID, reason), Level.ERROR, Level.ERROR);
                    if (!krista) {
                        return;
                    }
                }

                // Maps that link samples, pools, and run IDs
                Map<String, Set<String>> SamplesAndRuns;

                //Pull sample specific QC for this Request:
                Map<String, Set<String>> TempSamplesAndRuns = getSampSpecificQC(requestID, drm, user);
                SamplesAndRuns = getPoolSpecificQC(request, user);

                Map<String, Set<String>> pool_samp_comparison = compareSamplesAndRuns(SamplesAndRuns, poolRuns, TempSamplesAndRuns, sampleRuns, requestID, manualDemux);

                SamplesAndRuns.clear();

                SamplesAndRuns = checkPostSeqQC(apiUser, request, pool_samp_comparison);

                if (SamplesAndRuns.size() == 0) {
                    if (!forced && !manualDemux) {
                        System.err.println("[ERROR] No sequencing runs found for this Request ID.");
                        return;
                    } else if (manualDemux) {
                        logWarning("MANUAL DEMULTIPLEXING was performed. Nothing but the request file should be output.");
                    } else {
                        logWarning("ALERT: There are no sequencing runs passing QC for this run. Force is true, I will pull ALL samples for this project.");
                        force = true;
                    }
                }

                // Here, check to make sure all of the samples that passed have good read counts
                checkReadCounts();

                // get libType, also set ReqType if possible
                getLibTypes(passingSeqRuns, drm, apiUser, request);

                if (ReqType.isEmpty()) {
                    // Here I will pull the childs field recipe
                    recipes = getRecipe(drm, apiUser, request);
                    logWarning("RECIPE: " + getJoinedRecipes());
                    if (request.getPickListVal(Constants.REQUEST_NAME, apiUser).matches("(.*)PACT(.*)")) {
                        ReqType = Constants.IMPACT;
                    }

                    if (recipes.size() == 1 && recipes.get(0) == Recipe.SMAR_TER_AMP_SEQ) {
                        libTypes.add(LibType.SMARTER_AMPLIFICATION);
                        strand.add(Strand.NONE);
                        ReqType = Constants.RNASEQ;

                    }
                    if (ReqType.length() == 0) {
                        if (requestID.startsWith(Constants.REQUEST_05500)) {
                            logWarning("05500 project. This should be pulled as an impact.");
                            ReqType = Constants.IMPACT;
                        } else if (runAsExome) {
                            ReqType = Constants.EXOME;
                        } else {
                            logWarning("Request Name doesn't match one of the supported request types: " + request.getPickListVal(Constants.REQUEST_NAME, apiUser) + ". Information will be pulled as if it is an rnaseq/unknown run.");
                            ReqType = Constants.OTHER;
                        }
                    }
                }

                if (shiny && ReqType.equals(Constants.RNASEQ)) {
                    System.err.println("[ERROR] This is an RNASeq project, and you cannot grab this information yet via Shiny");
                    return;
                }

                // Get Samples that are supposed to be output, put them in this list!
                for (DataRecord child : request.getChildrenOfType(VeloxConstants.PLATE, apiUser)) {
                    for (DataRecord wells : child.getChildrenOfType(VeloxConstants.SAMPLE, apiUser)) {
                        String wellStat = wells.getSelectionVal(VeloxConstants.EXEMPLAR_SAMPLE_STATUS, apiUser);
                        if (wellStat.length() > 0) {
                            String sid = wells.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);
                            // Check to see if sample name is used already!
                            if (SampleListToOutput.containsKey(sid)) {
                                devLogger.error(String.format("This request has two samples that have the same name: %s", sid));
                                return;
                            }
                            if ((SamplesAndRuns.containsKey(sid)) || (force)) {
                                if (wells.getParentsOfType(VeloxConstants.SAMPLE, apiUser).size() > 0) {
                                    devLogger.warn("This sample is a tranfer from another request!");
                                    LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, wells, SamplesAndRuns, false, true);
                                    tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(requestID));
                                    SampleListToOutput.put(sid, tempHashMap);
                                } else {
                                    LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, wells, SamplesAndRuns, false, false);
                                    tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(requestID));
                                    SampleListToOutput.put(sid, tempHashMap);
                                }
                            }
                        }//if well status is not empty
                    } //end of platewell for loop
                }// end of plate loop

                for (DataRecord child : request.getChildrenOfType(VeloxConstants.SAMPLE, apiUser)) {
                    // Is this sample sequenced?
                    String sid = child.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);

                    // Added because we were getting samples that had the same name as a sequenced sample, but then it was failed so it shouldn't be used (as per Aaron).
                    String status = child.getSelectionVal(VeloxConstants.EXEMPLAR_SAMPLE_STATUS, apiUser);
                    if (status.equals("Failed - Completed")) {
                        devLogger.warn("Skipping " + sid + " because the sample is failed: " + status);
                        continue;
                    }

                    // This is never supposed to happen.
                    if (SampleListToOutput.containsKey(sid)) {
                        logError("This request has two samples that have the same name: " + sid, Level.ERROR, Level.ERROR);
                    }
                    // if this sample is in the list of
                    if ((SamplesAndRuns.containsKey(sid)) || (force)) {
                        if (child.getParentsOfType(VeloxConstants.SAMPLE, apiUser).size() > 0) {
                            devLogger.warn(String.format("Sample: %s is a tranfer from another request", sid));
                            LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, child, SamplesAndRuns, false, true);
                            tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(requestID));
                            SampleListToOutput.put(sid, tempHashMap);
                        } else {
                            LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, child, SamplesAndRuns, false, false);
                            tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(requestID));
                            SampleListToOutput.put(sid, tempHashMap);
                        }
                    }
                }// end of sample loop

                if (Objects.equals(ReqType, Constants.IMPACT) && runAsExome) {
                    ReqType = Constants.EXOME;
                }

                int numSamples = SampleListToOutput.size();
                if (numSamples == 0) {
                    logError("None of the samples in the project were found in the passing samples and runs. Please check the LIMs to see if the names are incorrect.", Level.ERROR, Level.ERROR);
                }

                // POOLED NORMAL START
                HashMap<DataRecord, HashSet<String>> PooledNormalSamples = SampleInfoImpact.getPooledNormals();
                if (PooledNormalSamples != null && PooledNormalSamples.size() > 0) {
                    devLogger.info(String.format("Number of Pooled Normal Samples: %d", PooledNormalSamples.size()));

                    // for each pooled normal, get the run ID from the pool, then add the sample and runID to that one variable.
                    SamplesAndRuns = addPooledNormalsToSampleList(SamplesAndRuns, RunID_and_PoolName, PooledNormalSamples, apiUser);

                    HashSet<DataRecord> keyCopy = new HashSet<>(PooledNormalSamples.keySet());

                    // Here go through the control samples, and get the info needed
                    for (DataRecord PNorms : keyCopy) {
                        String pNorm_name = PNorms.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);
                        LinkedHashMap<String, String> tempHashMap = getSampleInfoMap(apiUser, drm, PNorms, SamplesAndRuns, true, false);
                        tempHashMap.put(Constants.REQ_ID, Utils.getFullProjectNameWithPrefix(requestID));

                        // If include run ID is 'null' skip.
                        // This could mess up some older projects, so I may have to change this
                        if (tempHashMap.get(Constants.INCLUDE_RUN_ID) == null) {
                            logWarning("Skipping adding pooled normal info from " + tempHashMap.get(Constants.IGO_ID) + " because I cannot find include run id. ");
                            continue;
                        }

                        // If the sample pooled normal type (ex: FROZEN POOLED NORMAL) is already in the manfiest list
                        // Concatenate the include/ exclude run ids
                        if (SampleListToOutput.containsKey(pNorm_name) && tempHashMap.get(Constants.INCLUDE_RUN_ID) != null) {
                            devLogger.info(String.format("Combining Two Pooled Normals: %s", pNorm_name));

                            LinkedHashMap<String, String> originalPooledNormalSample = SampleListToOutput.get(pNorm_name);
                            Set<String> currIncludeRuns = new HashSet<>(Arrays.asList(originalPooledNormalSample.get(Constants.INCLUDE_RUN_ID).split(";")));

                            devLogger.info(String.format("OLD include runs: %s", originalPooledNormalSample.get(Constants.INCLUDE_RUN_ID)));
                            Set<String> currExcludeRuns = new HashSet<>(Arrays.asList(originalPooledNormalSample.get(Constants.EXCLUDE_RUN_ID).split(";")));

                            currIncludeRuns.addAll(Arrays.asList(tempHashMap.get(Constants.INCLUDE_RUN_ID).split(";")));
                            currExcludeRuns.addAll(Arrays.asList(originalPooledNormalSample.get(Constants.EXCLUDE_RUN_ID).split(";")));

                            tempHashMap.put(Constants.INCLUDE_RUN_ID, StringUtils.join(currIncludeRuns, ";"));
                            tempHashMap.put(Constants.EXCLUDE_RUN_ID, StringUtils.join(currExcludeRuns, ";"));
                        }

                        // Make sure SamplesAndRuns has the corrected RUN IDs
                        SamplesAndRuns.put(pNorm_name, new HashSet<>(Arrays.asList(tempHashMap.get(Constants.INCLUDE_RUN_ID).split(";"))));

                        // If bait set does not contain comma, the add. Comma means that the pooled normal has two different bait sets. This shouldn't happen, So I'm not adding them.
                        String thisBait = tempHashMap.get(Constants.CAPTURE_BAIT_SET);
                        if (!thisBait.contains(",")) {
                            SampleListToOutput.put(pNorm_name, tempHashMap);
                        }
                    }

                }
                // POOLED NORMAL END

                if (SampleInfo.isXenograftProject())
                    requestSpecies = RequestSpecies.XENOGRAFT;

                if (SampleListToOutput.size() != 0) {
                    // Grab Sample Renames
                    sampleRenames = SampleInfo.getSampleRenames();

                    // Grab sample information form queryProjectInfo
                    ArrayList<String> pInfo = new ArrayList<>(Arrays.asList(queryProjInfo(apiUser, drm, requestID)));

                    if (!pi.equals(Constants.NULL) && !invest.equals(Constants.NULL)) {
                        runNum = getRunNumber(requestID, pi, invest);
                        if (runNum > 1 && ReqType.equals(Constants.RNASEQ)) {
                            // Mono wants me to archive these, because sometimes Nick manually changes them.
                            // Get dir of final project location
                            // IF request file is there, search for date of last update
                            // Then copy to archive
                            String finalDir = String.valueOf(projectFilePath).replaceAll("drafts", ReqType);
                            File oldReqFile = new File(String.format("%s/%s_request.txt", finalDir, Utils.getFullProjectNameWithPrefix(requestID)));
                            if (oldReqFile.exists() && !force) {
                                String lastUpdated = getPreviousDateOfLastUpdate(oldReqFile);
                                copyToArchive(finalDir, requestID, lastUpdated, "_old");
                            }
                        }
                    }
                    pInfo.add("NumberOfSamples: " + String.valueOf(numSamples));

                    // Only grab values from SampleListToOutput to make an array (You don't need sample) (easier to iterate?)
                    ArrayList<LinkedHashMap<String, String>> sampInfo = new ArrayList<>(SampleListToOutput.values());
                    assignProjectSpecificInfo(sampInfo);
                    pInfo.add("Species: " + requestSpecies);

                    // This is done for the *smart* pairing (if necessary) and the grouping file (if necessary)
                    LinkedHashMap<String, Set<String>> patientSampleMap;

                    // make arrayListof hashmaps of sampleInfo, but only if they are !SAMPLE_CLASS.contains(Normal)
                    ArrayList<LinkedHashMap<String, String>> tumorSampInfo = new ArrayList<>();
                    for (LinkedHashMap<String, String> tempSamp : sampInfo) {
                        if (!tempSamp.get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL)) {
                            tumorSampInfo.add(tempSamp);
                        }
                    }

                    // Grab readme info - extra readme info is when there are samples with low coverage. It was requested to be saved in the readme file
                    String readmeInfo = request.getStringVal(Constants.READ_ME, apiUser) + " " + extraReadmeInfo;
                    sampInfo = getManualOverrides(readmeInfo, sampInfo);

                    if (ReqType.equals(Constants.RNASEQ) || ReqType.equals(Constants.OTHER)) {
                        projDir = new File(projectFilePath);
                        if (!projDir.exists()) {
                            projDir.mkdir();
                        }
                        log("Path: " + projDir, Level.INFO, Level.INFO);

                        if (!force) {
                            if (manualDemux) {
                                logWarning("Manual demux performed. I will not output maping file");
                            } else {
                                printMappingFile(SamplesAndRuns, requestID, projDir, baitVersion);
                            }
                        }

                        printRequestFile(pInfo, ampType, strand, requestID, projDir);
                        printReadmeFile(readmeInfo, requestID, projDir);

                        // criteria for making sample key excel
                        if (ReqType.equals(Constants.RNASEQ) && !libTypes.contains(LibType.TRU_SEQ_FUSION_DISCOVERY) && sampInfo.size() > 1) {
                            createSampleKeyExcel(requestID, projDir, SampleListToOutput);
                            // send sample key excel in an e-mail.
                        }
                    } else {
                        patientSampleMap = createPatientSampleMapping(SampleListToOutput);

                        File manDir = new File("manifests/" + Utils.getFullProjectNameWithPrefix(requestID));
                        if (draft) {
                            manDir = new File(projectFilePath);
                        }
                        if (!manDir.exists()) {
                            manDir.mkdirs();
                        }

                        // Add bait Version to pInfo
                        if (ReqType.equals(Constants.RNASEQ)) {
                            pInfo.add("DesignFile: NA");
                            pInfo.add("Assay: NA");
                            pInfo.add("TumorType: NA");
                        } else {
                            // Get Design File
                            String[] designs = baitVersion.split("\\+");
                            if (designs.length > 1) {
                                pInfo.add("DesignFile: ");//+ findDesignFile(designs[0]));
                                pInfo.add("SpikeinDesignFile: "); // + findDesignFile(designs[1]));
                                pInfo.add("AssayPath: ");
                            } else if (ReqType.equals(Constants.IMPACT)) {
                                pInfo.add("DesignFile: ");//+ findDesignFile(baitVersion));
                                pInfo.add("SpikeinDesignFile: "); //NA");
                                pInfo.add("AssayPath: ");
                            } else { // exome
                                pInfo.add("AssayPath: " + findDesignFile(baitVersion));
                            }
                            if (!baitVersion.equals(Constants.EMPTY)) {
                                if (Objects.equals(ReqType, Constants.EXOME)) {
                                    pInfo.add("Assay: " + baitVersion);
                                } else {
                                    pInfo.add("Assay: ");
                                }
                            } else {
                                pInfo.add("Assay: NA");
                            }

                            // Grab tumor type!
                            HashSet<String> tType = new HashSet<>();
                            for (LinkedHashMap<String, String> tempSamp : sampInfo) {
                                String t = tempSamp.get(Constants.ONCOTREE_CODE);
                                if (!t.equals(Constants.TUMOR) && !t.equals(Constants.NORMAL) && !t.equals(Constants.NA_LOWER_CASE) && !t.equals(Constants.UNKNOWN) && !t.equals(Constants.EMPTY)) {
                                    tType.add(t);
                                }
                            }
                            if (tType.isEmpty()) {
                                logWarning("I can't figure out the tumor type of this project. ");

                            } else if (tType.size() == 1) {
                                ArrayList<String> tumorTypes = new ArrayList<>(tType);
                                pInfo.add("TumorType: " + tumorTypes.get(0));
                            } else if (tType.size() > 1) {
                                pInfo.add("TumorType: mixed");// + tType);
                            } else {
                                pInfo.add("TumorType: NA");
                            }
                        }

                        String Manifest_filename = manDir + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_manifest.txt";
                        String pairing_filename = manDir + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_pairing.txt";
                        String grouping_filename = manDir + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_grouping.txt";
                        String patient_filename = manDir + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_patient.txt";
                        String clinical_filename = manDir + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_data_clinical.txt";

                        if (!force) {
                            printMappingFile(SamplesAndRuns, requestID, manDir, baitVersion);
                        }

                        printRequestFile(pInfo, requestID, manDir);
                        printHashMap(sampInfo, Manifest_filename);
                        printFileType(sampInfo, patient_filename, Constants.PATIENT);
                        printFileType(tumorSampInfo, clinical_filename, Constants.DATA_CLINICAL);

                        printGroupingFile(SampleListToOutput, patientSampleMap, grouping_filename);
                        printPairingFile(SampleListToOutput, drm, apiUser, pairing_filename, patientSampleMap);
                        printReadmeFile(readmeInfo, requestID, manDir);
                    }

                    // if there is an error in the running of this script
                    // delete all but mapping and request.
                    // IF there was a mapping issue and the mapping file may be wrong, change the name of the mapping file
                    // Also (TODO) add a specifc file with the errors causing the mapping issues
                    // For RNASEQ and other, nothing gets output except request and mapping files
                    // I will have to wait and figure out what to do, but int he meantime I will delete everything
                    if (Utils.exitLater && !krista && !requestID.startsWith(Constants.REQUEST_05500) && !ReqType.equals(Constants.RNASEQ) && !ReqType.equals(Constants.OTHER)) {
                        for (File f : filesCreated) {
                            if (isMappingFile(f)) {
                                File newName = new File(getErrorFile(f));
                                if (mappingIssue) {
                                    Files.move(f.toPath(), newName.toPath(), REPLACE_EXISTING);
                                } else if (newName.exists()) {
                                    newName.delete();
                                }
                            }
                            if (!isMappingFile(f) && !isRequestFile(f)) {
                                f.delete();
                            }
                        }
                    }

                    // if this is not a shiny run, and the project stuff is valid, copy to archive and add/append log files.
                    if (!shiny && !krista) {
                        DateFormat archiveDateFormat = new SimpleDateFormat("yyyyMMdd");
                        String date = archiveDateFormat.format(new Date());
                        copyToArchive(projectFilePath, requestID, date);
                        printToPipelineRunLog(requestID);
                    }
                }

            }// end of request
        } catch (Exception e) {
            devLogger.warn(e.getMessage(), e);
        } finally {
            File f = new File(projectFilePath);
            setPermissions(f);
        }
    }

    private void logError(String message, Level pmLogLevel, Level devLogLevel) {
        Utils.exitLater = true;
        pmLogger.log(pmLogLevel, message);
        devLogger.log(devLogLevel, message);
    }

    private String getErrorFile(File f) {
        return f.toString().replace(".txt", ".error");
    }

    private boolean isRequestFile(File f) {
        return f.getName().endsWith("request.txt");
    }

    private boolean isMappingFile(File f) {
        return f.getName().endsWith("sample_mapping.txt");
    }

    private ArrayList<LinkedHashMap<String, String>> getManualOverrides(String readmeInfo, ArrayList<LinkedHashMap<String, String>> sampInfo) {
        // Manual overrides are found in the readme file:
        // Current Manual overrides "OVERRIDE_BAIT_SET" - resents all bait sets (and assay) as whatever is there.
        // TODO: when list of overrides gets bigger, make it a list to search through.

        String[] lines = readmeInfo.split("\n");
        for (String line : lines) {
            if (line.startsWith(Constants.OVERRIDE_BAIT_SET)) {
                String[] overrideSplit = line.split(":");
                baitVersion = overrideSplit[overrideSplit.length - 1].trim();
                setNewBaitSet(sampInfo);
            }
        }
        return sampInfo;
    }

    private void setNewBaitSet(ArrayList<LinkedHashMap<String, String>> sampInfo) {
        String newBaitset;
        String newSpikein = Constants.NA_LOWER_CASE;
        if (baitVersion.contains("+")) {
            String[] bv_split = baitVersion.split("\\+");
            newBaitset = bv_split[0];
            newSpikein = bv_split[1];
        } else {
            newBaitset = baitVersion;
        }

        for (LinkedHashMap<String, String> tempSamp : sampInfo) {
            tempSamp.put(Constants.BAIT_VERSION, baitVersion);
            tempSamp.put(Constants.CAPTURE_BAIT_SET, newBaitset);
            tempSamp.put(Constants.SPIKE_IN_GENES, newSpikein);
        }
    }

    private void initializeFilePermissions() {
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

    }

    private void setPermissions(File f) {
        try {
            if (f.isDirectory()) {
                try {
                    Files.setPosixFilePermissions(f.toPath(), DIRperms);
                } catch (AccessDeniedException e) {
                }
                for (Path path : Files.newDirectoryStream(f.toPath())) {
                    File file = path.toFile();
                    if (file.isDirectory()) {
                        try {
                            Files.setPosixFilePermissions(Paths.get(file.getAbsolutePath()), DIRperms);
                        } catch (AccessDeniedException e) {
                        }
                        setPermissions(file);
                    } else {
                        try {
                            Files.setPosixFilePermissions(Paths.get(file.getAbsolutePath()), FILEperms);
                        } catch (AccessDeniedException e) {
                        }
                    }
                }
            }
        } catch (Exception e) {
            devLogger.warn(e.getMessage(), e);
        }
    }

    private List<Recipe> getRecipe(DataRecordManager drm, User apiUser, DataRecord request) {
        //this will get the recipes for all sampels under request
        List<DataRecord> samples;
        List<Object> recipes = new ArrayList<>();
        try {
            samples = Arrays.asList(request.getChildrenOfType(VeloxConstants.SAMPLE, apiUser));
            recipes = drm.getValueList(samples, VeloxConstants.RECIPE, apiUser);

        } catch (Exception e) {
            devLogger.warn(e.getMessage(), e);
        }

        return recipes.stream().map(r -> Recipe.getRecipeByValue(r.toString())).distinct().collect(Collectors.toList());
    }

    private Map<String, Set<String>> checkPostSeqQC(User apiUser, DataRecord request, Map<String, Set<String>> SampleRunHash) {
        // This will look through all post seq QC records
        // For each, if sample AND run is in map
        // then check the value of PostSeqQCStatus
        // if passing, continue
        // if failed, output WARNING that this sample failed post seq qc and WHY
        //            remove the run id

        // I'm going to add Sample ID and run ID here when I'm done yo! That way I can check and see if any
        // Don't have PostSeqAnalysisQC
        Map<String, Set<String>> finalList = new HashMap<>();

        try {
            List<DataRecord> postQCs = request.getDescendantsOfType(VeloxConstants.POST_SEQ_ANALYSIS_QC, apiUser);

            for (DataRecord post : postQCs) {
                String sampID = post.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);

                if (SampleRunHash.containsKey(sampID)) {
                    Set<String> runList = SampleRunHash.get(sampID);
                    String[] runParts = post.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, apiUser).split("_");
                    String runID = runParts[0] + "_" + runParts[1];

                    if (runList.contains(runID)) {
                        String status = String.valueOf(post.getPickListVal(VeloxConstants.POST_SEQ_QC_STATUS, apiUser));
                        if (!status.equals(Constants.PASSED)) {
                            String note = post.getStringVal(VeloxConstants.NOTE, apiUser);
                            note = note.replaceAll("\n", " ");
                            String message = String.format("Sample %s in run %s did not pass POST Sequencing QC(%s). The note attached says: %s. This will not be included in manifest.", sampID, runID, status, note);
                            logWarning(message);
                        } else {
                            if (!finalList.containsKey(sampID)) {
                                Set<String> temp = new HashSet<>();
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

                    if (runList.isEmpty()) {
                        SampleRunHash.remove(sampID);
                    }
                }

            }

        } catch (RemoteException | NotFound e) {
            devLogger.warn(e.getMessage(), e);
        }
        if (!SampleRunHash.isEmpty()) {
            for (String sampID : SampleRunHash.keySet()) {
                Set<String> runIDs = SampleRunHash.get(sampID);
                String message = String.format("Sample %s has runs that do not have POST Sequencing QC. We won't be able to tell if they are failed or not: %s. They will still be added to the sample list.", sampID, Arrays.toString(runIDs.toArray()));
                logWarning(message);
                if (!finalList.containsKey(sampID)) {
                    Set<String> temp = new HashSet<>();
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

    private void logWarning(String message) {
        pmLogger.log(PmLogPriority.WARNING, message);
        devLogger.warn(message);
    }

    private void checkReadCounts() {
        // For each sample in readsForSample if the number of reads is less than 1M, print out a warning
        for (String sample : readsForSample.keySet()) {
            Integer numReads = readsForSample.get(sample);
            if (numReads < 1000000) {
                // Print AND add to readme file
                extraReadmeInfo += "\n[WARNING] sample " + sample + " has less than 1M total reads: " + numReads + "\n";
                logWarning(String.format("sample %s has less than 1M total reads: %d", sample, numReads));
            }
        }
    }

    private String[] queryProjInfo(User apiUser, DataRecordManager drm, String requestID) {
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
        String[] pInfo = baos.toString().split("\n");

        pi = querySheet.getPI().split("@")[0];
        invest = querySheet.getInvest().split("@")[0];

        return pInfo;
    }

    private Map<String, Set<String>> addPooledNormalsToSampleList(Map<String, Set<String>> SamplesAndRuns, Map<String, String> RunID_and_PoolName, HashMap<DataRecord, HashSet<String>> PooledNormalSamples, User apiUser) {
        HashSet<String> runs = new HashSet<>();
        for (DataRecord rec : PooledNormalSamples.keySet()) {
            try {
                String sampName = rec.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);
                HashSet<String> pools = PooledNormalSamples.get(rec);
                for (String pool : pools) {
                    for (String runID : RunID_and_PoolName.keySet()) {
                        if (RunID_and_PoolName.get(runID).contains(pool)) {
                            runs.add(runID);
                        }
                    }
                }
                Set<String> t = new HashSet<>();
                if (SamplesAndRuns.containsKey(sampName)) {
                    t.addAll(SamplesAndRuns.get(sampName));
                }
                t.addAll(runs);
                SamplesAndRuns.put(sampName, t);
                runs.clear();
            } catch (Exception e) {
                devLogger.warn(e.getMessage(), e);
            }
        }

        return SamplesAndRuns;
    }

    private void assignProjectSpecificInfo(ArrayList<LinkedHashMap<String, String>> sampInfo) {
        // This will iterate the samples, grab the species, and if it is not the same and it
        // is not xenograft warning will be put.
        // If the species has been set to xenograft, it will give a warning if species is not human or xenograft
        Boolean bvChanged = false;
        for (LinkedHashMap<String, String> tempSamp : sampInfo) {

            // Skip pooled normal stuff
            if (tempSamp.get(Constants.SAMPLE_TYPE).equals(Constants.NORMAL_POOL) || tempSamp.get(Constants.SPECIES).equals(Constants.POOLNORMAL)) {
                continue;
            }

            try {
                RequestSpecies sampleSpecies = RequestSpecies.getSpeciesByValue(tempSamp.get(Constants.SPECIES));
                if (this.requestSpecies == RequestSpecies.XENOGRAFT) {
                    // Xenograft projects may only have samples of sampleSpecies human or xenograft
                    if (sampleSpecies != RequestSpecies.HUMAN && sampleSpecies != RequestSpecies.XENOGRAFT) {
                        logError("Request sampleSpecies has been determined as xenograft, but this sample is neither xenograft or human: " + sampleSpecies, PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                    }
                } else if (this.requestSpecies == null) {
                    this.requestSpecies = sampleSpecies;
                } else if (this.requestSpecies != sampleSpecies) {
                    // Requests that are not xenograft must have 100% the same sampleSpecies for each sample. If that is not true, it will output issue here:
                    logError("There seems to be a clash between sampleSpecies of each sample: Species for sample " + tempSamp.get(Constants.IGO_ID) + "=" + sampleSpecies + " Species for request so far=" + this.requestSpecies, PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                }
            } catch (Exception e) {
                devLogger.warn(String.format("Exception thrown while retrieving information about request species for request id: %s", Arguments.project));
            }

            //baitVerison - sometimes bait version needs to be changed. If so, the CAPTURE_BAIT_SET must also be changed
            if (Objects.equals(ReqType, Constants.RNASEQ) || Objects.equals(ReqType, Constants.OTHER)) {
                baitVersion = Constants.EMPTY;
            } else {
                String baitVersion = tempSamp.get(Constants.BAIT_VERSION);
                if (baitVersion != null && !baitVersion.isEmpty()) {
                    if (Objects.equals(ReqType, Constants.EXOME)) {
                        // First check xenograft, if yes, then if bait version is Agilent (manual bait version for exomes) change to xenograft version of Agilent
                        if (requestSpecies == RequestSpecies.XENOGRAFT && baitVersion.equals(Constants.MANUAL_EXOME_BAIT_VERSION_HUMAN)) {
                            baitVersion = Constants.MANUAL_EXOME_XENOGRAFT_BAIT_VERSION_HUMAN_MOUSE;
                            bvChanged = true;
                        }
                        String bv_sp = baitVersion;
                        // Test Bait version.
                        if (Objects.equals(findDesignFile(baitVersion), Constants.NA)) {
                            // Add species to end of baitVersion
                            String humanAbrevSpecies = Constants.HUMAN_ABREV;
                            String mouseAbrevSpecies = Constants.MOUSE_ABREV;
                            if (requestSpecies == RequestSpecies.HUMAN) {
                                bv_sp = baitVersion + "_" + humanAbrevSpecies;
                            } else if (requestSpecies == RequestSpecies.MOUSE) {
                                bv_sp = baitVersion + "_" + mouseAbrevSpecies;
                            } else if (requestSpecies == RequestSpecies.XENOGRAFT) {
                                bv_sp = baitVersion + "_" + humanAbrevSpecies + "_" + mouseAbrevSpecies;
                            }
                            if (!Objects.equals(bv_sp, baitVersion) && !Objects.equals(findDesignFile(bv_sp), Constants.NA)) {
                                this.baitVersion = bv_sp;
                                baitVersion = bv_sp;
                                //setNewBaitSet should be called
                                bvChanged = true;
                            }
                        }
                    }
                    if (!this.baitVersion.equals(baitVersion) && !this.baitVersion.equals(Constants.EMPTY)) {
                        logError(String.format("Request Bait version is not consistent: Current sample Bait version: %s. Bait version for request so far: %s", baitVersion, this.baitVersion), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                    } else if (this.baitVersion.equals(Constants.EMPTY)) {
                        this.baitVersion = baitVersion;
                    }
                }
            }
        }
        if (bvChanged) {
            setNewBaitSet(sampInfo);
        }
    }

    private void getLibTypes(ArrayList<DataRecord> passingSeqRuns, DataRecordManager drm, User apiUser, DataRecord request) {
        try {
            // ONE: Get ancestors of type sample from the passing seq Runs.
            List<List<DataRecord>> samplesFromSeqRun = drm.getAncestorsOfType(passingSeqRuns, VeloxConstants.SAMPLE, apiUser);

            // TWO: Get decendants of type sample from the request
            List<DataRecord> samplesFromRequest = request.getDescendantsOfType(VeloxConstants.SAMPLE, apiUser);

            Set<DataRecord> finalSampleList = new HashSet<>();
            // THREE: Get the overlap
            for (List<DataRecord> sampList : samplesFromSeqRun) {
                ArrayList<DataRecord> temp = new ArrayList<>(sampList);
                temp.retainAll(samplesFromRequest);
                finalSampleList.addAll(temp);
            }

            if (force) {
                finalSampleList.addAll(samplesFromRequest);
            }

            // Try finalSampleList FIRST. If this doesn't have any library types, just try samples from seq run.
            checkSamplesForLibTypes(finalSampleList, drm, apiUser);
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while retrieving information about Library Types for request id: %s", Arguments.project, e));
        }
    }

    private void checkSamplesForLibTypes(Set<DataRecord> finalSampleList, DataRecordManager drm, User apiUser) {
        try {
            // This is where I have to check all the overlapping samples for children of like 5 different types.
            for (DataRecord rec : finalSampleList) {
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_PROTOCOL, apiUser)), drm, apiUser)) {
                    for (DataRecord rnaProtocol : Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_PROTOCOL, apiUser))) {
                        try {
                            if (rnaProtocol.getBooleanVal(VeloxConstants.VALID, apiUser)) {
                                String exID = rnaProtocol.getStringVal(VeloxConstants.EXPERIMENT_ID, apiUser);
                                List<DataRecord> rnaExp = drm.queryDataRecords(VeloxConstants.TRU_SEQ_RNA_EXPERIMENT, "ExperimentId='" + exID + "'", apiUser);
                                if (rnaExp.size() != 0) {
                                    List<Object> strandedness = drm.getValueList(rnaExp, VeloxConstants.TRU_SEQ_STRANDING, apiUser);
                                    for (Object x : strandedness) {
                                        // Only check for Stranded, because older kits were not stranded and did not have this field, ie null"
                                        if (String.valueOf(x).equals(Constants.STRANDED)) {
                                            libTypes.add(LibType.TRU_SEQ_POLY_A_SELECTION_STRANDED);
                                            strand.add(Strand.REVERSE);
                                            ReqType = Constants.RNASEQ;
                                        } else {
                                            libTypes.add(LibType.TRU_SEQ_POLY_A_SELECTION_NON_STRANDED);
                                            strand.add(Strand.NONE);
                                            ReqType = Constants.RNASEQ;
                                        }
                                    }
                                }
                            }
                        } catch (NullPointerException e) {
                            System.err.println("[WARNING] You hit a null pointer exception while trying to find valid for library types. Please let BIC know.");
                        } catch (Exception e) {
                            devLogger.warn(String.format("Exception thrown while looking for valid for Library Types for request id: %s", Arguments.project), e);
                        }
                    }
                }
                if (Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RN_ASM_RNA_PROTOCOL_4, apiUser)).size() > 0) {
                    libTypes.add(LibType.TRU_SEQ_SM_RNA);
                    strand.add(Strand.EMPTY);
                    ReqType = Constants.RNASEQ;
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RIBO_DEPLETE_PROTOCOL_1, apiUser)), drm, apiUser)) {
                    libTypes.add(LibType.TRU_SEQ_RIBO_DEPLETE);
                    strand.add(Strand.REVERSE);
                    ReqType = Constants.RNASEQ;
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.TRU_SEQ_RNA_FUSION_PROTOCOL_1, apiUser)), drm, apiUser)) {
                    libTypes.add(LibType.TRU_SEQ_FUSION_DISCOVERY);
                    strand.add(Strand.NONE);
                    ReqType = Constants.RNASEQ;
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.SMAR_TER_AMPLIFICATION_PROTOCOL_1, apiUser)), drm, apiUser)) {
                    libTypes.add(LibType.SMARTER_AMPLIFICATION);
                    strand.add(Strand.NONE);
                    ReqType = Constants.RNASEQ;
                }
                if (checkValidBool(Arrays.asList(rec.getChildrenOfType(VeloxConstants.KAP_AM_RNA_STRANDED_SEQ_PROTOCOL_1, apiUser)), drm, apiUser)) {
                    libTypes.add(LibType.KAPA_M_RNA_STRANDED);
                    strand.add(Strand.REVERSE);
                    ReqType = Constants.RNASEQ;
                }
                if (rec.getChildrenOfType(VeloxConstants.NIMBLE_GEN_HYB_PROTOCOL_2, apiUser).length != 0) {
                    ReqType = Constants.IMPACT;
                }
                if (rec.getChildrenOfType(VeloxConstants.KAPA_AGILENT_CAPTURE_PROTOCOL_1, apiUser).length != 0) {
                    ReqType = Constants.EXOME;
                }
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while retrieving information about protocols for request id: %s", Arguments.project), e);
        }
    }

    private Boolean checkValidBool(List<DataRecord> recs, DataRecordManager drm, User apiUser) {
        if (recs == null || recs.size() == 0) {
            return false;
        }

        try {
            List<Object> valids = drm.getValueList(recs, VeloxConstants.VALID, apiUser);
            for (Object val : valids) {
                if (String.valueOf(val).equals("true")) {
                    return true;
                }
            }
        } catch (NullPointerException e) {
            System.err.println("[WARNING] You hit a null pointer exception while trying to find valid for library types. Please let BIC know.");
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while looking for valid for Library Types for request id: %s", Arguments.project), e);
        }
        return false;
    }

    private LinkedHashMap<String, String> getSampleInfoMap(User apiUser, DataRecordManager drm, DataRecord rec, Map<String, Set<String>> SamplesAndRuns, boolean poolNormal, boolean transfer) {
        LinkedHashMap<String, String> RequestInfo;
        // Latest attempt at refactoring the code. Why does species come up so much?
        SampleInfo LS2;
        if (ReqType.equals(Constants.IMPACT) || poolNormal) {
            LS2 = new SampleInfoImpact(ReqType, apiUser, drm, rec, SamplesAndRuns, force, poolNormal, transfer, deadLog);
        } else if (ReqType.equals(Constants.EXOME)) {
            LS2 = new SampleInfoExome(ReqType, apiUser, drm, rec, SamplesAndRuns, force, poolNormal, transfer, deadLog);
        } else {
            LS2 = new SampleInfo(ReqType, apiUser, drm, rec, SamplesAndRuns, force, poolNormal, transfer);
        }
        RequestInfo = LS2.SendInfoToMap();

        if (sampleAlias.keySet().contains(RequestInfo.get(Constants.CMO_SAMPLE_ID))) {
            RequestInfo.put(Constants.MANIFEST_SAMPLE_ID, sampleAlias.get(RequestInfo.get(Constants.CMO_SAMPLE_ID)));
        }

        if (badRuns.keySet().contains(RequestInfo.get(Constants.CMO_SAMPLE_ID))) {
            String excludeRuns = StringUtils.join(badRuns.get(RequestInfo.get(Constants.CMO_SAMPLE_ID)), ";");
            RequestInfo.put(Constants.EXCLUDE_RUN_ID, excludeRuns);
        }

        return RequestInfo;

    }

    private Map<String, Set<String>> getSampSpecificQC(String requestID, DataRecordManager drm, User apiUser) {
        // Here I will grab all Sample Speicfic QC that have request XXXX.
        // I will then populate a thing that will have all the samples as well as the runs they passed in.

        Map<String, Set<String>> sampleSpecificQC = new HashMap<>();

        try {
            List<DataRecord> SampleQCList = drm.queryDataRecords(VeloxConstants.SEQ_ANALYSIS_SAMPLE_QC, "Request = '" + requestID + "'", apiUser);

            // Basically do the same thing as the other thing.
            for (DataRecord rec : SampleQCList) {
                String RunStatus = String.valueOf(rec.getPickListVal(VeloxConstants.SEQ_QC_STATUS, apiUser));
                String[] runParts = rec.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, apiUser).split("_");
                String RunID = runParts[0] + "_" + runParts[1];
                String SampleID = rec.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);
                sampleRuns.add(RunID);

                if (!RunStatus.contains(Constants.PASSED)) {
                    if (RunStatus.contains(Constants.FAILED)) {
                        String message = String.format("Not including Sample %s from Run ID %s because it did NOT pass Sequencing Analysis QC: %s", SampleID, RunID, RunStatus);
                        log(message, PmLogPriority.SAMPLE_INFO, Level.INFO);
                        addToBadRuns(SampleID, RunID);
                    } else if (RunStatus.contains("Under-Review")) {
                        logError(String.format("Sample %s from RunID %s is still under review. I cannot guarantee this is DONE!", SampleID, RunID), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                        mappingIssue = true;
                    } else {
                        logError("Sample " + SampleID + " from RunID " + RunID + " needed additional reads. This status should change when the extra reads are sequenced. Please check. ", PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                        mappingIssue = true;
                    }
                    continue;
                }

                //PassingseqRuns - this is used to grab library type.
                passingSeqRuns.add(rec);
                int totalReads = 0;
                // Here I am going to pull the read count!
                try {
                    totalReads = (int) (long) rec.getLongVal(VeloxConstants.TOTAL_READS, apiUser);
                } catch (NullPointerException ignored) {
                }
                if (readsForSample.containsKey(SampleID)) {
                    Integer tempReads = readsForSample.get(SampleID) + totalReads;
                    readsForSample.put(SampleID, tempReads);
                } else {
                    readsForSample.put(SampleID, totalReads);
                }

                String alias = rec.getStringVal(VeloxConstants.SAMPLE_ALIASES, apiUser);

                // NOT DOING ANYTHING WITH ALIAS RIGHT NOW
                if (alias != null && !alias.isEmpty()) {
                    String message = "SAMPLE " + SampleID + " HAS AN ALIAS: " + alias + "!";
                    logWarning(message);
                    sampleAlias.put(SampleID, alias);
                }

                if (sampleSpecificQC.containsKey(SampleID)) {
                    Set<String> temp = sampleSpecificQC.get(SampleID);
                    temp.add(RunID);
                    sampleSpecificQC.put(SampleID, temp);
                } else {
                    Set<String> temp = new HashSet<>();
                    temp.add(RunID);
                    sampleSpecificQC.put(SampleID, temp);
                }
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while retrieving information about Sample specific QC for request id: %s", Arguments.project), e);
        }
        return sampleSpecificQC;
    }

    private void logSampleError(String message) {
        pmLogger.log(PmLogPriority.SAMPLE_ERROR, message);
        devLogger.error(message);
        Utils.exitLater = true;
    }

    private void addToBadRuns(String sample, String run) {
        if (badRuns.containsKey(sample)) {
            Set<String> temp = badRuns.get(sample);
            temp.add(run);
            badRuns.put(sample, temp);
        } else {
            Set<String> temp = new HashSet<>();
            temp.add(run);
            badRuns.put(sample, temp);
        }
    }

    private Map<String, Set<String>> compareSamplesAndRuns(Map<String, Set<String>> pool, Set<String> poolRuns, Map<String, Set<String>> sample, Set<String> sampleRuns, String requestID, boolean manualDemux) {
        // If sample and pool have the same number of samples and runIDs, return sample (more accurate information)
        // If pool has more RunIDs than sample, then use pool's data, with whatever sample specific overlap available.
        //     write warning about it
        // If sample has more run IDs than pool, send an error out because that should never happen.

        Map<String, Set<String>> FinalSamplesAndRuns = new HashMap<>();
        FinalSamplesAndRuns.putAll(sample);

        if (FinalSamplesAndRuns.size() == 0) {
            if (pool.size() != 0 && !manualDemux) {
                logError("For this project the sample specific QC is not present. My script is looking at the pool specific QC instead, which assumes that if the pool passed, every sample in the pool also passed. This is not necessarily true and you might want to check the delivery email to make sure it includes every sample name that is on the manifest. This doesn’t mean you can’t put the project through, it just means that the script doesn’t know if the samples failed sequencing QC.",
                        Level.ERROR, Level.ERROR);
                mappingIssue = true;
            }
            printPoolQCWarnings();
        } else if (poolQCWarnings.contains(Constants.ERROR)) {
            printPoolQCWarnings();
        }
        if (poolRuns.equals(sampleRuns)) {
            return FinalSamplesAndRuns;
        }

        // Start with sample data, go through pool and add any run Ids for samples that don't have them...
        Set<String> TempRunList = new HashSet<>();
        TempRunList.addAll(poolRuns);

        for (String run : sampleRuns) {
            TempRunList.remove(run);
        }

        if (TempRunList.size() > 0) {
            log("Sample specific QC is missing run(s) that pool QC contains. Will add POOL QC data for missing run(s): " + StringUtils.join(TempRunList, ", "), PmLogPriority.SAMPLE_INFO, Level.INFO);

            for (String samp : pool.keySet()) {
                // get run ids from pool sample that overlap with the ones sample qc is missing.
                Set<String> Temp2 = new HashSet<>();
                Temp2.addAll(pool.get(samp));
                Temp2.retainAll(TempRunList);

                if (Temp2.size() > 0) {
                    if (FinalSamplesAndRuns.containsKey(samp)) {
                        Set<String> temp = FinalSamplesAndRuns.get(samp);
                        temp.addAll(Temp2);
                        FinalSamplesAndRuns.put(samp, temp);
                    } else {
                        Set<String> temp = new HashSet<>();
                        temp.addAll(Temp2);
                        FinalSamplesAndRuns.put(samp, temp);
                    }
                }
            }
        }

        // Here go through and see if any runs are included in Sample QC and not Pool QC.
        TempRunList.clear();
        TempRunList.addAll(sampleRuns);

        for (String run : poolRuns) {
            TempRunList.remove(run);
        }

        return FinalSamplesAndRuns;

    }

    private void log(String message, Level pmLogLevel, Level devLogLevel) {
        pmLogger.log(pmLogLevel, message);
        devLogger.log(devLogLevel, message);
    }

    private void printPoolQCWarnings() {
        for (PriorityAwareLogMessage poolQCWarning : poolQCWarnings) {
            pmLogger.log(poolQCWarning.getPriority(), poolQCWarning.getMessage());
            if (poolQCWarning.getPriority() == PmLogPriority.POOL_ERROR)
                devLogger.error(poolQCWarning.getMessage());
            else
                devLogger.info(poolQCWarning.getMessage());
        }
    }

    private Map<String, Set<String>> getPoolSpecificQC(DataRecord request, User apiUser) {
        Map<String, Set<String>> SamplesAndRuns = new HashMap<>();
        try {
            String reqID = request.getStringVal(VeloxConstants.REQUEST_ID, apiUser);
            // Get the first sample data records for the request
            ArrayList<DataRecord> originalSampRec = new ArrayList<>();
            ArrayList<DataRecord> samp = new ArrayList<>(request.getDescendantsOfType(VeloxConstants.SAMPLE, apiUser));
            for (DataRecord s : samp) {
                ArrayList<DataRecord> reqs = new ArrayList<>(s.getParentsOfType(VeloxConstants.REQUEST, apiUser));
                ArrayList<DataRecord> plates = new ArrayList<>(s.getParentsOfType(VeloxConstants.PLATE, apiUser));
                if (!reqs.isEmpty()) {
                    originalSampRec.add(s);
                } else if (!plates.isEmpty()) {
                    for (DataRecord p : plates) {
                        if (p.getParentsOfType(VeloxConstants.REQUEST, apiUser).size() != 0) {
                            originalSampRec.add(s);
                            break;
                        }
                    }
                }
            }

            // For each run qc, find the sample
            ArrayList<DataRecord> sequencingRuns = new ArrayList<>(request.getDescendantsOfType(VeloxConstants.SEQ_ANALYSIS_QC, apiUser));
            for (DataRecord seqrun : sequencingRuns) {

                if (!verifySeqRun(seqrun, reqID, apiUser)) {
                    continue;
                }
                String RunStatus = String.valueOf(seqrun.getPickListVal(VeloxConstants.SEQ_QC_STATUS, apiUser));

                if (RunStatus.isEmpty()) {
                    RunStatus = String.valueOf(seqrun.getEnumVal(VeloxConstants.QC_STATUS, apiUser));
                }

                // Try to get RunID, if not try to get pool name, if not again, then error!
                String RunID;
                String runPath = seqrun.getStringVal(VeloxConstants.SEQUENCER_RUN_FOLDER, apiUser).replaceAll("/$", "");
                if (runPath.length() > 0) {
                    String[] pathList = runPath.split("/");
                    String runName = pathList[(pathList.length - 1)];
                    String pattern = "^(\\d)+(_)([a-zA-Z]+_[\\d]{4})(_)([a-zA-Z\\d\\-_]+)";
                    RunID = runName.replaceAll(pattern, "$3");
                } else {
                    RunID = seqrun.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);
                }

                if ((!RunStatus.contains(Constants.PASSED)) && (!RunStatus.equals("0"))) {
                    if (RunStatus.contains(Constants.FAILED)) {
                        String message = "Skipping Run ID " + RunID + " because it did NOT pass Sequencing Analysis QC: " + RunStatus;
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_INFO, message));
                        poolRuns.add(RunID);
                        continue;
                    } else if (RunStatus.contains("Under-Review")) {
                        String pool = seqrun.getStringVal(VeloxConstants.SAMPLE_ID, apiUser);
                        String message = "RunID " + RunID + " is still under review for pool " + pool + " I cannot guarantee this is DONE!";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        Utils.exitLater = true;
                        mappingIssue = true;
                    } else {
                        String message = "RunID " + RunID + " needed additional reads. I cannot tell yet if they were finished. Please check.";
                        poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                        poolRuns.add(RunID);
                        Utils.exitLater = true;
                        mappingIssue = true;
                    }
                } else {
                    poolRuns.add(RunID);
                }
                passingSeqRuns.add(seqrun);

                if (Objects.equals(RunID, Constants.NULL)) {
                    String message = "Unable to find run path or related sample ID for this sequencing run";
                    poolQCWarnings.add(new PriorityAwareLogMessage(PmLogPriority.POOL_ERROR, message));
                    Utils.exitLater = true;
                    mappingIssue = true;
                }

                RunID_and_PoolName = linkPoolToRunID(RunID_and_PoolName, RunID, seqrun.getStringVal(VeloxConstants.SAMPLE_ID, apiUser));

                // Populate Samples and Runs
                List<DataRecord> samplesFromSeqRun = seqrun.getAncestorsOfType(VeloxConstants.SAMPLE, apiUser);
                samplesFromSeqRun.retainAll(originalSampRec);

                for (DataRecord s1 : samplesFromSeqRun) {
                    String sname = s1.getStringVal(VeloxConstants.OTHER_SAMPLE_ID, apiUser);
                    if (SamplesAndRuns.containsKey(sname)) {
                        Set<String> temp = SamplesAndRuns.get(sname);
                        temp.add(RunID);
                        SamplesAndRuns.put(sname, temp);
                    } else {
                        Set<String> temp = new HashSet<>();
                        temp.add(RunID);
                        SamplesAndRuns.put(sname, temp);
                    }
                }
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while retrieving information about Pool specific QC for request id: %s", Arguments.project), e);
        }
        return SamplesAndRuns;
    }

    private boolean verifySeqRun(DataRecord seqrun, String reqID, User apiUser) {
        // This is to verify this sequencing run is found
        // Get the parent sample data type
        // See what the request ID (s) are, search for reqID.
        // Return (is found?)

        boolean sameReq = false;

        try {
            List<DataRecord> parentSamples = seqrun.getParentsOfType(VeloxConstants.SAMPLE, apiUser);
            for (DataRecord p : parentSamples) {
                String reqs = p.getStringVal(VeloxConstants.REQUEST_ID, apiUser);
                List<String> requests = Arrays.asList(reqs.split("\\s*,\\s*"));
                sameReq = requests.contains(reqID);
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while verifying sequence run for request id: %s", Arguments.project), e);
        }
        return sameReq;
    }

    private String getPreviousDateOfLastUpdate(File request) {
        String date = "NULL";
        try (BufferedReader reader = new BufferedReader(new FileReader(request))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("DateOfLastUpdate: ")) {
                    return line.split(": ")[1].replaceAll("-", "");
                }
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while retrieving information date of last update for request id: %s", Arguments.project), e);
        }

        return date;
    }

    private void createSampleKeyExcel(String requestID, File projDir, LinkedHashMap<String, LinkedHashMap<String, String>> sampleHashMap) {
        // sample info
        File sampleKeyExcel = new File(projDir.getAbsolutePath() + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_key.xlsx");

        //create the workbook
        XSSFWorkbook wb = new XSSFWorkbook();

        // Create a sheet in the workbook
        XSSFSheet sampleKey = wb.createSheet(Constants.SAMPLE_KEY);

        //set the row number
        int rowNum = 0;

        // Protect the whole sheet, unlock the cells that need unlocking
        sampleKey.protectSheet(Constants.BANANNA_GRAM_72);

        CellStyle unlockedCellStyle = wb.createCellStyle();
        unlockedCellStyle.setLocked(false);

        // First put the directions.
        String instructs = "Instructions:\n    - Fill in the GroupName column for each sample.\n        - Please do not leave any blank fields in this column.\n        - Please be consistent when assigning group names." +
                "\n        - GroupNames are case sensitive. For example, “Normal” and “normal” will be identified as two different group names.\n        - GroupNames should start with a letter  and only use characters, A-Z and 0-9. " +
                "Please do not use any special characters (i.e. '&', '#', '$' etc) or spaces when assigning a GroupName.\n    - Please only edit column C. Do not make any other changes to this file.\n        " +
                "- Do not change any of the information in columns A or B.\n        - Do not rename the samples IDs (InvestigatorSampleID or FASTQFileID). If you have a question about the sample names, please email " +
                "bic-request@cbio.mskcc.org.\n        - Do not reorder or rename the columns.\n        - Do not use the GroupName column to communicate any other information (such as instructions, comments, etc)";

        sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Collections.singletonList(instructs)), rowNum, Constants.Excel.INSTRUCTIONS);
        rowNum++;

        //header
        sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Arrays.asList("FASTQFileID", "InvestigatorSampleID", "GroupName")), rowNum, "header");
        rowNum++;

        ArrayList<String> sids = new ArrayList<>(sampleHashMap.keySet());
        Collections.sort(sids);

        // add each sample
        for (String hashKey : sids) {
            LinkedHashMap<String, String> hash = sampleHashMap.get(hashKey);
            String investSamp = hash.get(Constants.INVESTIGATOR_SAMPLE_ID);
            String seqIGOid = hash.get(Constants.SEQ_IGO_ID);
            if (seqIGOid == null) {
                seqIGOid = hash.get(Constants.IGO_ID);
            }

            String sampName1 = hash.get(Constants.CMO_SAMPLE_ID);
            String cmoSamp = sampName1 + "_IGO_" + seqIGOid;
            if (cmoSamp.startsWith("#")) {
                cmoSamp = sampName1 + "_IGO_" + seqIGOid;
            }
            sampleKey = addRowToSheet(wb, sampleKey, new ArrayList<>(Arrays.asList(cmoSamp, investSamp)), rowNum, null);

            // Unlock this rows third cell
            Row thisRow = sampleKey.getRow(rowNum);
            Cell cell3 = thisRow.createCell(2);  // zero based
            cell3.setCellStyle(unlockedCellStyle);

            rowNum++;
        }

        // EMPTY cell conditional formatting: color cell pink if there is nothing in it:
        SheetConditionalFormatting sheetCF = sampleKey.getSheetConditionalFormatting();
        ConditionalFormattingRule emptyRule = sheetCF.createConditionalFormattingRule(ComparisonOperator.EQUAL, "\"\"");
        PatternFormatting fill1 = emptyRule.createPatternFormatting();
        fill1.setFillBackgroundColor(IndexedColors.YELLOW.index);

        CellRangeAddress[] regions = {CellRangeAddress.valueOf("A1:C" + rowNum)};
        sheetCF.addConditionalFormatting(regions, emptyRule);

        // DATA VALIDATION
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sampleKey);
        XSSFDataValidationConstraint dvConstraint = (XSSFDataValidationConstraint) dvHelper.createTextLengthConstraint(ComparisonOperator.GE, "15", null);
        CellRangeAddressList rangeList = new CellRangeAddressList();
        rangeList.addCellRangeAddress(CellRangeAddress.valueOf("A1:C" + rowNum));
        XSSFDataValidation dv1 = (XSSFDataValidation) dvHelper.createValidation(dvConstraint, rangeList);
        dv1.setShowErrorBox(true);
        sampleKey.addValidationData(dv1);

        // Lastly auto size the three columns I am using:
        sampleKey.autoSizeColumn(0);
        sampleKey.autoSizeColumn(1);
        sampleKey.autoSizeColumn(2);


        // Add extra sheet called Example that will have the example
        XSSFSheet exampleSheet = wb.createSheet(Constants.EXAMPLE);
        rowNum = 0;
        exampleSheet.protectSheet(Constants.BANANNA_GRAM_72);

        // There are a couple different examples so I would like to
        // grab them from a tab-delim text file, and row by row add them to the excel.

        try {
            InputStream exStream = ClassLoader.getSystemResourceAsStream(sampleKeyExamplesPath);
            DataInputStream in = new DataInputStream(exStream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String exLine;

            while ((exLine = br.readLine()) != null) {
                String type = null;
                if (exLine.startsWith(Constants.CORRECT)) {
                    type = Constants.CORRECT;
                } else if (exLine.startsWith(Constants.INCORRECT)) {
                    type = Constants.INCORRECT;
                }

                String[] cellVals = exLine.split("\t");

                exampleSheet = addRowToSheet(wb, exampleSheet, new ArrayList<>(Arrays.asList(cellVals)), rowNum, type);
                rowNum++;
            }

        } catch (Exception e) {
            devLogger.warn(String.format("An Exception was thrown while creating sample key excel file: %s", sampleKeyExcel), e);
        }
        // the example sheet has a header for each example, so I can't auto size that column.
        exampleSheet.setColumnWidth(0, (int) (exampleSheet.getColumnWidth(0) * 2.2));
        exampleSheet.autoSizeColumn(1);
        exampleSheet.autoSizeColumn(2);

        try {
            FileOutputStream fileOUT = new FileOutputStream(sampleKeyExcel);
            filesCreated.add(sampleKeyExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while writing to file: %s", sampleKeyExcel), e);
        }
    }

    private void printPairingExcel(String pairing_filename, LinkedHashMap<String, String> pair_Info, ArrayList<String> exome_normal_list) {
        File pairingExcel = new File(pairing_filename.substring(0, pairing_filename.lastIndexOf('.')) + ".xlsx");

        XSSFWorkbook wb = new XSSFWorkbook();
        XSSFSheet pairingInfo = wb.createSheet(Constants.PAIRING_INFO);
        int rowNum = 0;

        pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<>(Arrays.asList(Constants.TUMOR, Constants.MATCHED_NORMAL, Constants.SAMPLE_RENAME)), rowNum, Constants.EXCEL_ROW_TYPE_HEADER);
        rowNum++;

        for (String tum : pair_Info.keySet()) {
            String norm = pair_Info.get(tum);
            if (Objects.equals(ReqType, Constants.EXOME) && exome_normal_list.contains(tum)) {
                norm = tum;
                tum = Constants.NA_LOWER_CASE;
            }

            pairingInfo = addRowToSheet(wb, pairingInfo, new ArrayList<>(Arrays.asList(tum, norm)), rowNum, null);
            rowNum++;
        }

        try {
            //Now that the excel is done, print it to file
            FileOutputStream fileOUT = new FileOutputStream(pairingExcel);
            filesCreated.add(pairingExcel);
            wb.write(fileOUT);
            fileOUT.close();
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while writing to file: %s", pairingExcel), e);
        }
    }

    private void printHashMap(ArrayList<LinkedHashMap<String, String>> Hmap, String filename) {
        try {
            char[] alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
            String ExcelFileName = filename.substring(0, filename.lastIndexOf('.')) + ".xlsx";
            XSSFWorkbook wb = new XSSFWorkbook();
            XSSFSheet sampleInfo = wb.createSheet(Constants.Manifest.SAMPLE_INFO);
            int rowNum = 0;

            // Quickly make SampleRenames sheet, fill in correctly with NOTHing in it
            XSSFSheet sampleRenames = wb.createSheet(Constants.Manifest.SAMPLE_RENAMES);
            sampleRenames = addRowToSheet(wb, sampleRenames, new ArrayList<>(Arrays.asList(Constants.Manifest.OLD_NAME, Constants.Manifest.NEW_NAME)), rowNum, Constants.EXCEL_ROW_TYPE_HEADER);

            if (NewMappingScheme == 1) {
                for (LinkedHashMap<String, String> row : Hmap) {
                    rowNum++;
                    ArrayList<String> replaceNames = new ArrayList<>(Arrays.asList(row.get(Constants.MANIFEST_SAMPLE_ID), row.get(Constants.CORRECTED_CMO_ID)));
                    sampleRenames = addRowToSheet(wb, sampleRenames, replaceNames, rowNum, null);
                }
            }

            rowNum = 0;

            String[] header0 = hashMapHeader.toArray(new String[hashMapHeader.size()]);
            ArrayList<String> header = new ArrayList<>(Arrays.asList(header0));

            // Fixing header names
            ArrayList<String> replace = new ArrayList<>(Arrays.asList(manualMappingHashMap.split(",")));
            for (String fields : replace) {
                String[] parts = fields.split(":");
                int indexHeader = header.indexOf(parts[0]);
                header.set(indexHeader, parts[1]);
            }

            try {
                // Print header:
                sampleInfo = addRowToSheet(wb, sampleInfo, header, rowNum, Constants.EXCEL_ROW_TYPE_HEADER);
                rowNum++;

                // output each line, in order!
                for (LinkedHashMap<String, String> row : Hmap) {
                    sampleInfo = addRowToSheet(sampleInfo, row, rowNum);
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
                String lastSpot;
                if (header.size() > 26) {
                    int firstLetter = header.size() / 26;
                    int remainder = header.size() - (26 * firstLetter);
                    lastSpot = alphabet[firstLetter - 1] + alphabet[remainder - 1] + String.valueOf(Hmap.size() + 1);
                } else {
                    lastSpot = alphabet[header.size() - 1] + String.valueOf(Hmap.size() + 1);
                }
                CellRangeAddress[] regions = {CellRangeAddress.valueOf("A1:" + lastSpot)};

                sheetCF.addConditionalFormatting(regions, hashTagRule);

                //Now that the excel is done, print it to file
                FileOutputStream fileOUT = new FileOutputStream(ExcelFileName);
                wb.write(fileOUT);
                filesCreated.add(new File(ExcelFileName));
                fileOUT.close();

            } catch (Exception e) {
                devLogger.warn(String.format("Exception thrown while writing to file: %s", ExcelFileName), e);
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while creating mapping file", e));
        }
    }

    private XSSFSheet addRowToSheet(XSSFWorkbook wb, XSSFSheet sheet, ArrayList<String> list, int rowNum, String type) {
        try {
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum = 0;
            for (String val : list) {
                if (val == null || val.isEmpty()) {
                    val = Constants.Excel.EMPTY;
                }
                XSSFCell cell = row.createCell(cellNum++);
                XSSFCellStyle style = wb.createCellStyle();
                XSSFFont headerFont = wb.createFont();

                if (type != null) {
                    if (type.equals(Constants.EXCEL_ROW_TYPE_HEADER)) {
                        headerFont.setBold(true);
                        style.setFont(headerFont);
                    }
                    if (type.equals(Constants.Excel.INSTRUCTIONS)) {
                        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 6));
                        style.setWrapText(true);
                        row.setRowStyle(style);
                        int lines = 2;
                        int pos = 0;
                        while ((pos = val.indexOf("\n", pos) + 1) != 0) {
                            lines++;
                        }
                        row.setHeight((short) (row.getHeight() * lines));
                    }
                    if (type.equals(Constants.CORRECT)) {
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.GREEN.getIndex());
                        style.setFont(headerFont);
                    }
                    if (type.equals(Constants.INCORRECT)) {
                        headerFont.setBold(true);
                        headerFont.setColor(IndexedColors.RED.getIndex());
                        style.setFont(headerFont);
                    }
                }

                cell.setCellStyle(style);
                cell.setCellValue(val);
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while adding row to xlsx sheet"), e);
        }
        return sheet;
    }

    private XSSFSheet addRowToSheet(XSSFSheet sheet, LinkedHashMap<String, String> map, int rowNum) {
        try {
            ArrayList<String> header = new ArrayList<>(hashMapHeader);
            // If this is the old mapping scheme, don't make the CMO Sample ID include IGO ID.
            if (NewMappingScheme == 0) {
                int indexHeader = header.indexOf(Constants.MANIFEST_SAMPLE_ID);
                header.set(indexHeader, Constants.CMO_SAMPLE_ID);
            }
            XSSFRow row = sheet.createRow(rowNum);
            int cellNum = 0;
            for (String key : header) {
                if (map.get(key) == null || map.get(key).isEmpty()) {
                    if (exceptionList.contains(key)) {
                        map.put(key, Constants.NA_LOWER_CASE);
                        if (Objects.equals(key, Constants.Excel.SPECIMEN_COLLECTION_YEAR)) {
                            map.put(key, "000");
                        }
                    } else if (silentList.contains(key)) {
                        map.put(key, "");
                    } else {
                        map.put(key, Constants.Excel.EMPTY);
                    }
                }

                row.createCell(cellNum++).setCellValue(map.get(key));
            }
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while adding row to xlsx sheet"), e);
        }
        return sheet;
    }

    private String findDesignFile(String assay) {
        if (Objects.equals(ReqType, Constants.IMPACT) || Objects.equals(ReqType, Constants.EXOME)) {
            File dir = new File(designFilePath + "/" + assay);
            if (dir.isDirectory()) {
                if (Objects.equals(ReqType, Constants.IMPACT)) {
                    File berger = new File(dir.getAbsolutePath() + "/" + assay + "__DESIGN__LATEST.berger");
                    if (berger.isFile()) {
                        try {
                            return berger.getCanonicalPath();
                        } catch (Throwable ignored) {
                        }
                    } else if (Objects.equals(ReqType, Constants.IMPACT) && !runAsExome) {
                        logError(String.format("Cannot find design file for assay %s", assay), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                    }
                } else { // exome
                    for (File iterDirContents : Utils.getFilesInDir(dir)) {
                        if (iterDirContents.toString().endsWith("targets.ilist")) {
                            return dir.toString();
                        }
                    }
                    // None of the contents of this dir was a targets.ilist file. This Dir is not an exome dir
                    return Constants.NA;
                }
                return dir.toString();
            }
        }
        return Constants.NA;
    }

    private void printMappingFile(Map<String, Set<String>> SamplesAndRuns, String requestID, File projDir, String baitVersion) {
        File mappingFile = null;

        try {
            HashSet<String> runsWithMultipleFolders = new HashSet<>();

            String mappingFileContents = "";
            for (String sample : new ArrayList<>(SamplesAndRuns.keySet())) {
                Set<String> runIDs = SamplesAndRuns.get(sample);
                HashSet<String> sample_pattern = new HashSet<>();

                if (sampleAlias.keySet().contains(sample)) {
                    ArrayList<String> aliasSampleNames = new ArrayList<>(Arrays.asList(sampleAlias.get(sample).split(";")));
                    for (String aliasName : aliasSampleNames) {
                        String message = String.format("Sample %s has alias %s", sample, aliasName);
                        logWarning(message);
                        sample_pattern.add(aliasName.replaceAll("[_-]", "[-_]"));
                    }
                } else {
                    sample_pattern.add(sample.replaceAll("[_-]", "[-_]"));
                }

                // Here Find the RUN ID. Iterate through each directory in fastq_path so I can search through each FASTQ directory
                // Search each /FASTQ/ directory for directories that start with "RUN_ID"
                // Take the newest one?

                // This takes the best guess for the run id, and has bash fill out the missing parts!
                for (String id : runIDs) {
                    final String id2 = id;
                    String RunIDFull;

                    //Iterate through fastq_path
                    File dir = new File(fastq_path + "/hiseq/FASTQ/");

                    File[] files = dir.listFiles((dir1, name) -> name.startsWith(id2));

                    // find out how many run IDs came back
                    if (files == null) {
                        String message = String.format("No directories for run ID %s found.", id);
                        logWarning(message);
                        continue;
                    }
                    if (files.length == 0) {
                        logError(String.format("Could not find sequencing run folder for Run ID: %s", id), Level.ERROR, Level.ERROR);
                        mappingIssue = true;
                        return;
                    } else if (files.length > 1) {
                        // Here I will remove any directories that do NOT have the project as a folder in the directory.
                        ArrayList<File> runsWithProjectDir = new ArrayList<>();
                        for (File runDir : files) {
                            File requestPath = new File(runDir.getAbsoluteFile().toString() + "/Project_" + requestID);
                            if (requestPath.exists() && requestPath.isDirectory()) {
                                runsWithProjectDir.add(runDir);
                            }
                        }
                        files = runsWithProjectDir.toArray(new File[runsWithProjectDir.size()]);
                        if (files == null) {
                            String message = "No run ids with request id found.";
                            logWarning(message);
                            continue;
                        }
                        if (files.length == 0) {
                            logError(String.format("Could not find sequencing run folder that also contains request %s for Run ID: %s", requestID, id), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                            mappingIssue = true;
                            return;
                        } else if (files.length > 1) {
                            Arrays.sort(files);
                            String foundFiles = StringUtils.join(files, ", ");
                            if (!runsWithMultipleFolders.contains(id)) {
                                String message = String.format("More than one sequencing run folder found for Run ID %s: %s I will be picking the newest folder.", id, foundFiles);
                                logWarning(message);
                                runsWithMultipleFolders.add(id);
                            }
                            RunIDFull = files[files.length - 1].getAbsoluteFile().getName().toString();
                        } else {
                            RunIDFull = files[0].getAbsoluteFile().getName();
                        }
                    } else {
                        RunIDFull = files[0].getAbsoluteFile().getName();
                    }

                    // Grab RUN ID, save it for the request file.
                    runIDlist.add(RunIDFull);

                    for (String S_Pattern : sample_pattern) {
                        String pattern;
                        if (!sample.equals("FFPEPOOLEDNORMAL") && !sample.equals("FROZENPOOLEDNORMAL") && !sample.equals("MOUSEPOOLEDNORMAL")) {
                            pattern = dir.toString() + "/" + RunIDFull + "*/Proj*" + requestID.replaceFirst("^0+(?!$)", "") + "/Sample_" + S_Pattern;
                        } else {
                            pattern = dir.toString() + "/" + RunIDFull + "*/Proj*" + "/Sample_" + S_Pattern + "*";
                        }

                        String cmd = "ls -d " + pattern;

                        Process pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                        pr.waitFor();

                        int exit = pr.exitValue();
                        if (exit != 0) {
                            String igoID = SampleListToOutput.get(sample).get(Constants.IGO_ID);
                            String seqID = SampleListToOutput.get(sample).get(Constants.SEQ_IGO_ID);

                            cmd = "ls -d " + pattern + "_IGO_" + seqID + "*";

                            pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                            pr.waitFor();
                            exit = pr.exitValue();

                            if (exit != 0) {
                                cmd = "ls -d " + pattern + "_IGO_*";

                                pr = new ProcessBuilder("/bin/bash", "-c", cmd).start();
                                pr.waitFor();
                                exit = pr.exitValue();

                                if (exit != 0) {
                                    logError(String.format("Error while trying to find fastq Directory for %s it is probably mispelled, or has an alias.", sample), Level.ERROR, Level.ERROR);
                                    BufferedReader bufE = new BufferedReader(new InputStreamReader(pr.getErrorStream()));
                                    while (bufE.ready()) {
                                        System.err.println("[ERROR] " + bufE.readLine());
                                    }
                                    mappingIssue = true;
                                    continue;
                                } else {
                                    NewMappingScheme = 1;
                                }
                            } else {
                                // this working means that I have to change the cmo sample id to have the seq iD.
                                if (!seqID.equals(igoID)) {
                                    String manifestSampleID = SampleListToOutput.get(sample).get(Constants.MANIFEST_SAMPLE_ID).replace("IGO_" + igoID, "IGO_" + seqID);
                                    SampleListToOutput.get(sample).put(Constants.MANIFEST_SAMPLE_ID, manifestSampleID);
                                }
                                NewMappingScheme = 1;
                            }
                        }
                        String sampleFqPath = StringUtils.chomp(IOUtils.toString(pr.getInputStream()));

                        if (sampleFqPath.isEmpty()) {
                            continue;
                        }

                        String[] paths = sampleFqPath.split("\n");

                        // Find out if this is single end or paired end by looking inside the directory for a R2_001.fastq.gz
                        for (String p : paths) {
                            if ((sample.contains("POOLEDNORMAL") && (sample.contains("FFPE") || sample.contains("FROZEN"))) && !p.matches("(.*)IGO_" + baitVersion.toUpperCase() + "_[ATCG](.*)")) {
                                continue;
                            }

                            String paired = "SE";
                            File sampleFq = new File(p);
                            File[] listOfFiles = sampleFq.listFiles();
                            for (File f1 : listOfFiles) {
                                if (f1.getName().endsWith("_R2_001.fastq.gz")) {
                                    paired = "PE";
                                    break;
                                }
                            }

                            // Confirm there is a SampleSheet.csv in the path:
                            File samp_sheet = new File(p + "/SampleSheet.csv");
                            if (!samp_sheet.isFile() && ReqType.equals(Constants.IMPACT)) {
                                if (shiny) {
                                    System.out.print("[ERROR] ");
                                } else {
                                    System.out.print("[WARNING] ");
                                }
                                String message = String.format("Sample %s from run %s does not have a sample sheet in the sample directory. This will not pass the validator.", sample, RunIDFull);
                                devLogger.warn(message);
                            }


                            // FLATTEN ALL sample ids herei:
                            String sampleName = sampleNormalization(sample);
                            if (sampleSwap.size() > 0 && sampleSwap.keySet().contains(sample)) {
                                sampleName = sampleNormalization(sampleSwap.get(sample));
                            }
                            if (sampleRenames.size() > 0 && sampleRenames.keySet().contains(sample)) {
                                sampleName = sampleNormalization(sampleRenames.get(sample));
                            }

                            mappingFileContents += "_1\t" + sampleName + "\t" + RunIDFull + "\t" + p + "\t" + paired + "\n";
                        }
                    }
                }
            }

            if (mappingFileContents.length() > 0) {
                mappingFileContents = filterToAscii(mappingFileContents);
                mappingFile = new File(projDir.getAbsolutePath() + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_mapping.txt");
                PrintWriter pW = new PrintWriter(new FileWriter(mappingFile, false), false);
                filesCreated.add(mappingFile);
                pW.write(mappingFileContents);
                pW.close();
            }

        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while creating mapping file: %s", mappingFile), e);
        }
    }

    private void printPairingFile(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput, DataRecordManager drm, User apiUser, String pairing_filename, LinkedHashMap<String, Set<String>> patientSampleMap) {
        try {
            LinkedHashMap<String, String> igo_tumor_info = new LinkedHashMap<>();
            LinkedHashMap<String, String> pair_Info = new LinkedHashMap<>();
            List<String> warnings = new ArrayList<>();
            ArrayList<String> exome_normal_list = new ArrayList<>();

            HashSet<String> CorrectedCMOids = new HashSet<>();

            // Make a list of IGO ids to give to printPairingFile script
            ArrayList<String> igoIDs = new ArrayList<>();
            for (String samp : SampleListToOutput.keySet()) {
                igoIDs.add(SampleListToOutput.get(samp).get(Constants.IGO_ID));
                igo_tumor_info.put(SampleListToOutput.get(samp).get(Constants.IGO_ID), samp);
                CorrectedCMOids.add(SampleListToOutput.get(samp).get(Constants.CORRECTED_CMO_ID));
            }

            // Go through each igo id and try and find a SamplePairing data record,
            for (String id : igoIDs) {
                String cmoID = igo_tumor_info.get(id);
                List<DataRecord> records = drm.queryDataRecords(VeloxConstants.SAMPLE_PAIRING, "SampleId = '" + id + "'", apiUser);
                if (records.size() == 0) {
                    if (!id.startsWith("CTRL-")) {
                        warnings.add(String.format("No pairing record for igo ID '%s", id));
                    }
                    continue;
                }

                // If Sample Class doesn't have "Normal" in it, sample is tumor
                // Then save as Normal,Tumor
                DataRecord rec = records.get(records.size() - 1);
                if (!SampleListToOutput.get(cmoID).get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL)) {
                    String tum = SampleListToOutput.get(igo_tumor_info.get(id)).get(Constants.CORRECTED_CMO_ID);
                    String norm = rec.getStringVal(VeloxConstants.SAMPLE_PAIR, apiUser);

                    if (norm.isEmpty()) {
                        if (ReqType == Constants.EXOME) {
                            norm = Constants.NA_LOWER_CASE;
                        } else {
                            continue;
                        }
                    }

                    norm = norm.replaceAll("\\s", "");
                    if ((!CorrectedCMOids.contains(norm)) && (!force) && (!norm.equals(Constants.NA_LOWER_CASE))) {
                        System.err.println("[WARNING] Normal matching with this tumor is NOT a valid sample in this request: Tumor: " + tum + " Normal: " + norm + " The normal will be changed to na.");
                        norm = Constants.NA_LOWER_CASE;
                    }
                    if (pair_Info.containsKey(tum) && !Objects.equals(pair_Info.get(tum), norm)) {
                        logError(String.format("Tumor is matched with two different normals. I have no idea how this happened! Tumor: %s Normal: %s", tum, norm), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                        printWarnings(warnings);

                        return;
                    }
                    pair_Info.put(tum, norm);
                } else if (ReqType.equals(Constants.EXOME)) {
                    // Add normals to a list, check at the end that all normals are in the pairing file. Otherwise add them with na.
                    exome_normal_list.add(cmoID);
                }
            }
            for (String NormIds : exome_normal_list) {
                if (!pair_Info.containsKey(NormIds) && !pair_Info.containsValue(NormIds)) {
                    pair_Info.put(NormIds, Constants.NA_LOWER_CASE);
                }
            }

            if (pair_Info.size() > 0) {
                printWarnings(warnings);
            } else if (!patientSampleMap.isEmpty()) {
                // There were no pairing sample records, do smart pairing
                // For each patient ID, separate samples by normal and tumor. Then if there is at least 1 normal, match it with all the tumors.
                // TODO: This will have to become populated into the LIMs Perhaps send a signal to another script that will suck it into the LIMs.

                log("Pairing records not found, trying smart Pairing", org.apache.log4j.Level.INFO, Level.INFO);
                pair_Info = smartPairing(SampleListToOutput, patientSampleMap);
            } else {
                logWarning("No Pairing File will be output.");
            }

            //Done going through all igo ids, now print
            if (pair_Info.size() > 0) {
                File pairing_file = new File(pairing_filename);
                PrintWriter pW = new PrintWriter(new FileWriter(pairing_file, false), false);
                filesCreated.add(pairing_file);
                for (String tum : pair_Info.keySet()) {
                    String norm = pair_Info.get(tum);
                    if (Objects.equals(ReqType, Constants.EXOME) && exome_normal_list.contains(tum)) {
                        norm = tum;
                        tum = Constants.NA_LOWER_CASE;
                    }
                    pW.write(sampleNormalization(norm) + "\t" + sampleNormalization(tum) + "\n");
                }
                pW.close();

                if (shiny || krista) {
                    printPairingExcel(pairing_filename, pair_Info, exome_normal_list);
                }
            }

        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while creating pairing file: %s", pairing_filename), e);
        }
    }

    private void printWarnings(List<String> warnings) {
        for (String warning : warnings) {
            devLogger.warn(warning);
        }
    }

    private LinkedHashMap<String, String> smartPairing(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput, LinkedHashMap<String, Set<String>> patientSampleMap) {
        // for each patient sample, go through the set of things and find out if they are tumor or normal.
        // for each tumor, match with the first normal.
        // no normal? put na
        // no tumor? don't add to pair_Info
        LinkedHashMap<String, String> pair_Info = new LinkedHashMap<>();
        for (String patient : patientSampleMap.keySet()) {
            ArrayList<String> tumors = new ArrayList<>();
            ArrayList<String> allNormals = new ArrayList<>();
            // populating the tumors and normals for this patient
            for (String s : patientSampleMap.get(patient)) {
                LinkedHashMap<String, String> samp = SampleListToOutput.get(s);
                if (samp.get(Constants.SAMPLE_CLASS).contains(Constants.NORMAL)) {
                    allNormals.add(s);
                } else {
                    tumors.add(s);
                }
            }

            //go through each tumor, add it to the pair_Info with a normal if possible
            for (String t : tumors) {
                LinkedHashMap<String, String> tum = SampleListToOutput.get(t);
                String corrected_t = tum.get(Constants.CORRECTED_CMO_ID);
                String PRESERVATION = tum.get(Constants.SPECIMEN_PRESERVATION_TYPE);
                String SITE = tum.get(Constants.TISSUE_SITE);
                if (allNormals.size() > 0) {
                    ArrayList<String> normals = new ArrayList<>(allNormals);
                    ArrayList<String> preservationNormals = new ArrayList<>();
                    ArrayList<String> useTheseNormals = new ArrayList<>();
                    // cycle through and find out if you have normal with same tumor sample preservation type
                    for (String n : allNormals) {
                        LinkedHashMap<String, String> norm = SampleListToOutput.get(n);
                        if (norm.get(Constants.SPECIMEN_PRESERVATION_TYPE).equals(PRESERVATION)) {
                            preservationNormals.add(n);
                        }
                    }
                    // Now if any match preservation type, use those normals to continue matching
                    if (preservationNormals.size() > 0) {
                        //print("Number that matched preservation: " + String.valueOf(preservationNormals.size()));
                        normals = new ArrayList<>(preservationNormals);
                        //print("New Normals to choose from " + normals);
                    }
                    // go through and see if any of the normals have the same tissue site.
                    for (String n : normals) {
                        LinkedHashMap<String, String> norm = SampleListToOutput.get(n);
                        if (norm.get(Constants.TISSUE_SITE).equals(SITE)) {
                            useTheseNormals.add(n);
                        }
                    }
                    // If there are more than one, just pick the first.
                    String n;
                    if (useTheseNormals.size() > 0) {
                        n = useTheseNormals.get(0);
                    } else {
                        n = normals.get(0);
                    }
                    LinkedHashMap<String, String> norm = SampleListToOutput.get(n);
                    pair_Info.put(corrected_t, norm.get(Constants.CORRECTED_CMO_ID));
                } else {
                    // no normal, for now put NA
                    pair_Info.put(corrected_t, Constants.NA_LOWER_CASE);
                }
            }
        }

        return pair_Info;

    }

    private String patientNormalization(String sample) {
        sample = sample.replace("-", "_");
        if (!sample.equals(Constants.NA_LOWER_CASE)) {
            sample = "p_" + sample;
        }
        return sample;
    }

    private String sampleNormalization(String sample) {
        sample = sample.replace("-", "_");
        if (!sample.equals(Constants.NA_LOWER_CASE)) {
            sample = "s_" + sample;
        }
        return sample;
    }

    private LinkedHashMap<String, Set<String>> createPatientSampleMapping(LinkedHashMap<String, LinkedHashMap<String, String>> SampleList) {
        LinkedHashMap<String, Set<String>> tempMap = new LinkedHashMap<>();
        for (String sid : SampleList.keySet()) {
            LinkedHashMap<String, String> rec = SampleList.get(sid);
            String pid = rec.get(Constants.CMO_PATIENT_ID);
            if (pid.startsWith("#")) {
                String message = String.format("Cannot make smart mapping because Patient ID is emtpy or has an issue: %s", pid);
                logWarning(message);
                return new LinkedHashMap<>();
            }
            if (tempMap.containsKey(pid)) {
                Set<String> tempSet = tempMap.get(pid);
                tempSet.add(sid);
                tempMap.put(pid, tempSet);
            } else {
                Set<String> tempSet = new HashSet<>();
                tempSet.add(sid);
                tempMap.put(pid, tempSet);
            }
        }

        return tempMap;
    }

    private void printFileType(ArrayList<LinkedHashMap<String, String>> sampInfo, String outputFilename, String fileType) {
        String outputText = "";
        String manualHeader = "";

        if (Objects.equals(fileType, Constants.DATA_CLINICAL)) {
            manualHeader = manualClinicalHeader;
        } else if (Objects.equals(fileType, Constants.PATIENT)) {
            manualHeader = manualMappingPatientHeader;
        }

        // Create map for maping
        LinkedHashMap<String, String> fieldMapping = new LinkedHashMap<>();
        ArrayList<String> fieldList = new ArrayList<>(Arrays.asList(manualHeader.split(",")));
        for (String fields : fieldList) {
            String[] parts = fields.split(":");
            fieldMapping.put(parts[0], parts[1]);
        }

        // add header
        outputText += StringUtils.join(fieldMapping.keySet(), "\t");
        outputText += "\n";
        outputText = filterToAscii(outputText);

        for (LinkedHashMap<String, String> samp : sampInfo) {
            ArrayList<String> line = new ArrayList<>();
            for (String key : fieldMapping.values()) {
                String val = samp.get(key);

                if (key.equals(Constants.CMO_SAMPLE_ID) || key.equals(Constants.INVESTIGATOR_SAMPLE_ID)) {
                    val = sampleNormalization(val);
                }
                if (key.equals(Constants.CMO_PATIENT_ID) || key.equals(Constants.INVESTIGATOR_PATIENT_ID)) {
                    val = patientNormalization(val);
                }
                if (key.equals(Constants.SAMPLE_CLASS) && fileType.equals(Constants.PATIENT)) {
                    if (!val.contains(Constants.NORMAL)) {
                        val = Constants.TUMOR;
                    }
                }
                line.add(val);
            }
            outputText += StringUtils.join(line, "\t");
            outputText += "\n";
        }

        if (outputText.length() > 0) {
            try {
                outputText = filterToAscii(outputText);
                File outputFile = new File(outputFilename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                filesCreated.add(outputFile);
                pW.write(outputText);
                pW.close();
            } catch (Exception e) {
                devLogger.warn(String.format("Exception thrown while creating file: %s", outputFilename), e);
            }
        }
    }

    private void printGroupingFile(LinkedHashMap<String, LinkedHashMap<String, String>> SampleListToOutput, LinkedHashMap<String, Set<String>> patientSampleMap, String outputFilename) {
        String outputText = "";
        DecimalFormat df = new DecimalFormat("000");
        int count = 0;
        if (patientSampleMap.isEmpty()) {
            String message = "No patient sample map, therefore no grouping file created.";
            logWarning(message);
            return;
        }
        for (String k : patientSampleMap.keySet()) {
            Set<String> tempSet = patientSampleMap.get(k);
            for (String i : tempSet) {
                LinkedHashMap<String, String> sampInfo = SampleListToOutput.get(i);
                outputText += sampleNormalization(sampInfo.get(Constants.CORRECTED_CMO_ID)) + "\tGroup_" + df.format(count) + "\n";
            }
            count++;
        }
        if (outputText.length() > 0) {
            try {
                outputText = filterToAscii(outputText);
                File outputFile = new File(outputFilename);
                PrintWriter pW = new PrintWriter(new FileWriter(outputFile, false), false);
                filesCreated.add(outputFile);
                pW.write(outputText);
                pW.close();
            } catch (Exception e) {
                devLogger.warn(String.format("Exception thrown while creating grouping file: %s", outputFilename), e);
            }
        }
    }

    private void printRequestFile(ArrayList<String> pInfo, String requestID, File projDir) {
        printRequestFile(pInfo, new HashSet<>(), new HashSet<>(), requestID, projDir);
    }

    private void printRequestFile(ArrayList<String> pInfo, Set<String> ampType, Set<Strand> strand, String requestID, File projDir) {
        // This will change the fields of the pInfo array, and print out the correct field
        // It will also pr/int all the ampType and libTypes, and species.

        String requestFileContents = "";

        if (ReqType.equals(Constants.EXOME)) {
            requestFileContents += "Pipelines: variants\n";
            requestFileContents += "Run_Pipeline: variants\n";
        } else if (ReqType.equals(Constants.IMPACT)) {
            requestFileContents += "Pipelines: \n";
            requestFileContents += "Run_Pipeline: \n";
        } else if (ReqType.equals(Constants.RNASEQ)) {
            requestFileContents += "Run_Pipeline: rnaseq\n";
        } else if (recipes.size() == 1 && recipes.get(0) == Recipe.CH_IP_SEQ) {
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
        for (String line : pInfo) {
            String[] splitLine = line.split(": ", 2);
            if (splitLine.length == 1) {
                continue;
                //splitLine = new String[] {splitLine[0], "NA"};
            } else if (splitLine[1].isEmpty() || Objects.equals(splitLine[1], Constants.EMPTY)) {
                splitLine[1] = Constants.NA;
            }

            if (convertFieldNames.containsKey(splitLine[0])) {
                if (splitLine[0].endsWith("_E-mail")) {
                    if (splitLine[0].equals("Requestor_E-mail")) {
                        requestFileContents += "Investigator_E-mail: " + splitLine[1] + "\n";
                    }
                    if (splitLine[0].equals("Lab_Head_E-mail")) {
                        requestFileContents += "PI_E-mail: " + splitLine[1] + "\n";
                    }
                    String[] temp = splitLine[1].split("@");
                    splitLine[1] = temp[0];
                }
                requestFileContents += convertFieldNames.get(splitLine[0]) + ": " + splitLine[1] + "\n";
            } else if (splitLine[0].contains("IGO_Project_ID") || splitLine[0].equals(Constants.PROJECT_ID)) {
                requestFileContents += "ProjectID: Proj_" + splitLine[1] + "\n";
            } else if (splitLine[0].contains("Platform") || splitLine[0].equals("Readme_Info") || splitLine[0].equals("Sample_Type") || splitLine[0].equals("Bioinformatic_Request")) {
                continue;
            } else if (splitLine[0].equals("Project_Manager")) {
                String[] tempName = splitLine[1].split(", ");
                if (tempName.length > 1) {
                    requestFileContents += splitLine[0] + ": " + tempName[1] + " " + tempName[0] + "\n";
                } else {
                    requestFileContents += splitLine[0] + ": " + splitLine[1] + "\n";
                }
            } else {
                requestFileContents += line + "\n";
            }
            if (splitLine[0].equals("Requestor_E-mail")) {
                invest = splitLine[1];
            }
            if (splitLine[0].equals("Lab_Head_E-mail")) {
                pi = splitLine[1];
            }
        }

        if (invest.isEmpty() || pi.isEmpty()) {
            logError(String.format("Cannot create run number because PI and/or Investigator is missing. %s %s", pi, invest), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
        } else {
            if (runNum != 0) {
                requestFileContents += "RunNumber: " + runNum + "\n";
            }
        }
        if (runNum > 1) {
            if (!shiny && (rerunReason == null || rerunReason.isEmpty())) {
                logError("This generation of the project files is a rerun, but no rerun reason was given. Use option 'rerunReason' to give a reason for this rerun in quotes. ", PmLogPriority.SAMPLE_ERROR, Level.ERROR);
                Utils.exitLater = true;
                return;
            }
            requestFileContents += "Reason_for_rerun: " + rerunReason + "\n";

        }
        requestFileContents += "RunID: " + getJoinedCollection(runIDlist, ", ") + "\n";

        requestFileContents += "Institution: cmo\n";

        if (Objects.equals(ReqType, Constants.OTHER)) {
            requestFileContents += "Recipe: " + getJoinedRecipes() + "\n";
        }

        if (Objects.equals(ReqType, Constants.RNASEQ)) {
            requestFileContents += "AmplificationTypes: " + getJoinedCollection(ampType, ", ") + "\n";
            requestFileContents += "LibraryTypes: " + getJoinedLibTypes() + "\n";

            if (strand.size() > 1) {
                String message = "Multiple strandedness options found!";
                logWarning(message);
            }

            requestFileContents += "Strand: " + getJoinedCollection(strand, ", ") + "\n";
            requestFileContents += "Pipelines: ";
            // If pipeline_options (command line) is not empty, put here. remember to remove the underscores
            // For now I am under the assumption that this is being passed correctly.
            if (pipeline_options != null && pipeline_options.length > 0) {
                String pipelines = getJoinedCollection(Arrays.asList(pipeline_options), ", ").replace("_", " ");
                requestFileContents += pipelines;
            } else {
                // Default is Alignment STAR, Gene Count, Differential Gene Expression
                requestFileContents += "NULL, RNASEQ_STANDARD_GENE_V1, RNASEQ_DIFFERENTIAL_GENE_V1";
            }
            requestFileContents += "\n";
        } else {
            requestFileContents += "AmplificationTypes: NA\n";
            requestFileContents += "LibraryTypes: NA\n";
            requestFileContents += "Strand: NA\n";
        }

        // adding projectFolder back
        if (ReqType.equals(Constants.IMPACT) || ReqType.equals(Constants.EXOME)) {
            requestFileContents += "ProjectFolder: " + String.valueOf(projDir).replaceAll("BIC/drafts", "CMO") + "\n";
        } else {
            requestFileContents += "ProjectFolder: " + String.valueOf(projDir).replaceAll("drafts", ReqType) + "\n";
        }

        // Date of last update
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        Date date = new Date();
        requestFileContents += "DateOfLastUpdate: " + dateFormat.format(date) + "\n";

        if (!noPortal && !ReqType.equals(Constants.RNASEQ)) {
            printPortalConfig(projDir, requestFileContents, requestID);
        }

        try {
            requestFileContents = filterToAscii(requestFileContents);
            File requestFile = new File(projDir.getAbsolutePath() + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_request.txt");
            PrintWriter pW = new PrintWriter(new FileWriter(requestFile, false), false);
            filesCreated.add(requestFile);
            pW.write(requestFileContents);
            pW.close();
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while creating request file: %s", requestFileContents), e);
        }
    }

    private String getJoinedLibTypes() {
        return getJoinedCollection(libTypes, ",");
    }

    private <T> String getJoinedCollection(Collection<T> collection, String delimiter) {
        return collection.stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }

    private String getJoinedRecipes() {
        return getJoinedCollection(recipes, ",");
    }

    private void printPortalConfig(File projDir, String requestFileContents, String requestID) {
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

        String replaceText = ReqType;
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
                    if (Objects.equals(ReqType, Constants.IMPACT)) {
                        assay = parts[1].toUpperCase();
                    } else {
                        assay = parts[1];
                    }
                }
                configFileContents += configRequestMap.get(parts[0]) + "=\"" + parts[1] + "\"\n";
            }

            // Change path depending on where it should be going
            if (ReqType.equals(Constants.IMPACT) || ReqType.equals(Constants.EXOME)) {
                dataClinicalPath = String.valueOf(projDir).replaceAll("BIC/drafts", "CMO") + "\n";
            } else {
                dataClinicalPath = String.valueOf(projDir).replaceAll("drafts", replaceText) + "\n";
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
        if (!dataClinicalPath.isEmpty()) {
            configFileContents += "data_clinical=\"" + dataClinicalPath + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_sample_data_clinical.txt\"\n";
        } else {
            String message = String.format("Cannot find path to data clinical file: %s. Not included in portal config", dataClinicalPath);
            logWarning(message);
            configFileContents += "data_clinical=\"\"\n";
        }
        File configFile = null;

        try {
            configFileContents = filterToAscii(configFileContents);
            configFile = new File(projDir.getAbsolutePath() + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_portal_conf.txt");
            filesCreated.add(configFile);
            PrintWriter pW = new PrintWriter(new FileWriter(configFile, false), false);
            pW.write(configFileContents);
            pW.close();
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while creating portal config file: %s", configFile), e);
        }
    }

    private int getRunNumber(String requestID, String pi, String invest) {
        final String req = requestID;
        File resultDir = new File(String.format("%s/%s/%s", resultsPathPrefix, pi, invest));

        File[] files = resultDir.listFiles((dir, name) -> name.endsWith(req.replaceFirst("^0+(?!$)", "")));
        if (files != null && files.length == 1) {
            File projectResultDir = files[0];
            File[] files2 = projectResultDir.listFiles((dir, name) -> name.startsWith("r_"));
            if (files2.length > 0) {
                int[] runs = new int[files2.length];
                int index = 0;
                for (File f : files2) {
                    int pastRuns = Integer.parseInt(f.getName().replaceFirst("^r_", ""));
                    runs[index] = pastRuns;
                    index++;
                }

                Arrays.sort(runs);

                return runs[runs.length - 1] + 1;
            }
        }
        logWarning("Could not determine PIPELINE RUN NUMBER from delivery directory. Setting to: 1. If this is incorrect, email cmo-project-start@cbio.mskcc.org");

        return 1;
    }

    private void printToPipelineRunLog(String requestID) {
        boolean newFile = false;
        // If this project already has a pipeline run log add rerun information to it.
        // IF the rerun number has already been marked in there, just add to it....
        // Create file is not created before:
        File archiveProject = new File(archivePath + "/" + Utils.getFullProjectNameWithPrefix(requestID));

        if (!archiveProject.exists()) {
            devLogger.info(String.format("Creating archive directory: %s", archiveProject));
            archiveProject.mkdirs();
        }

        File runLogFile = new File(archiveProject + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_runs.log");
        if (!runLogFile.exists()) {
            newFile = true;
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        String reason = Constants.NA;
        if (rerunReason != null && !rerunReason.isEmpty()) {
            reason = rerunReason;
        }

        try {
            PrintWriter pw = new PrintWriter(new FileWriter(runLogFile, true), false);

            if (newFile) {
                pw.write("Date\tTime\tRun_Number\tReason_For_Rerun\n");
            }

            pw.println(dateFormat.format(now) + "\t" + timeFormat.format(now) + "\t" + runNum + "\t" + reason);

            pw.close();
        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while creating run log file: %s", runLogFile), e);
        }
    }

/*

 This is where all functions that happen after printing project files.
*/

    private void copyToArchive(String fromPath, String requestID, String dateDir) {
        copyToArchive(fromPath, requestID, dateDir, "");
    }

    private void copyToArchive(String fromPath, String requestID, String dateDir, String suffix) {
        File curDir = new File(fromPath);
        File projDir = new File(String.format("%s/%s/%s", archivePath, Utils.getFullProjectNameWithPrefix(requestID), dateDir));

        try {
            if (curDir.exists() && curDir.isDirectory() && Utils.getFilesInDir(curDir).size() > 0) {
                if (!projDir.exists()) {
                    projDir.mkdirs();
                }
                for (File f : Utils.getFilesInDir(curDir)) {
                    File to = new File(String.format("%s/%s%s", projDir, f.getName(), suffix));
                    if (f.isDirectory()) {
                        FileUtils.copyDirectory(f, to);
                        continue;
                    }
                    Files.copy(f.toPath(), to.toPath(), REPLACE_EXISTING);
                    setPermissions(f);
                }
            } else {
                logError(String.format("Cannot copy project files to archive directory: %s, the current directory: %s is not valid or has no files.",
                        projDir, curDir), PmLogPriority.SAMPLE_ERROR, Level.ERROR);
            }

        } catch (Exception e) {
            devLogger.warn(String.format("Exception thrown while copying files from: %s to archive: %s", curDir, projDir), e);
        }
    }

    private void printReadmeFile(String fileText, String requestID, File projDir) {
        if (fileText.length() > 0) {
            File readmeFile = null;
            try {
                fileText = filterToAscii(fileText);
                readmeFile = new File(projDir.getAbsolutePath() + "/" + Utils.getFullProjectNameWithPrefix(requestID) + "_README.txt");
                PrintWriter pW = new PrintWriter(new FileWriter(readmeFile, false), false);
                filesCreated.add(readmeFile);
                pW.write(fileText + "\n");
                pW.close();
            } catch (Exception e) {
                devLogger.warn(String.format("Exception thrown while creating readme file: %s", readmeFile), e);
            }
        }
    }

    private Map<String, String> linkPoolToRunID(Map<String, String> RunID_and_PoolName, String RunID, String poolName) {
        if (RunID_and_PoolName.containsKey(RunID)) {
            RunID_and_PoolName.put(RunID, RunID_and_PoolName.get(RunID) + ";" + poolName);
        } else {
            RunID_and_PoolName.put(RunID, poolName);
        }

        return RunID_and_PoolName;
    }

    private String filterToAscii(String highUnicode) {
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
        public void run() {
            CreateManifestSheet.closeConnection();
        }
    }
}


