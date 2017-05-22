package org.mskcc.kickoff.lims;

//These were already here or added by me as needed
import java.io.*;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.text.DecimalFormat;
import java.lang.reflect.Field;
import org.apache.commons.lang3.StringUtils;
import java.io.FileOutputStream;
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
import com.sampullara.cli.*;

/**
 * SampleInfoImpact<br>Purpose: this is a class extending SampleInfo so that we can grab all necessary information from
 * extra data records as needed for IMPACT/dmp pipeline
 **/
public class SampleInfoImpact extends SampleInfo
{
    /** All necessary fields for IMPACT requests. **/
    protected List<String> base_fields = new ArrayList(Arrays.asList("IGO_ID", "EXCLUDE_RUN_ID", "INCLUDE_RUN_ID", "INVESTIGATOR_PATIENT_ID", "INVESTIGATOR_SAMPLE_ID", "SAMPLE_CLASS",
                                                                     "SAMPLE_TYPE", "SPECIMEN_PRESERVATION_TYPE", "SPECIES", "STATUS", "MANIFEST_SAMPLE_ID", "CORRECTED_CMO_ID",
                                                                     "BARCODE_ID", "BARCODE_INDEX", "CMO_SAMPLE_ID", "LIBRARY_INPUT", "SPECIMEN_COLLECTION_YEAR", "ONCOTREE_CODE",
                                                                     "TISSUE_SITE", "SEX", "LIBRARY_YIELD", "CMO_PATIENT_ID", "CAPTURE_NAME", "CAPTURE_INPUT",
                                                                     "CAPTURE_CONCENTRATION", "BAIT_VERSION", "CAPTURE_BAIT_SET", "SPIKE_IN_GENES"));
    protected HashMap<String, String> field_defaults = new HashMap<String,String>();
    
    private static HashMap<DataRecord, HashSet<String>> pooledNormals;
    private static HashSet<String> poolNameList = new HashSet<String>();
    // Consensus BaitSet
    private static String ConsensusBaitSet;
    private static String ConsensusSpikeIn;
   
    private static DecimalFormat df = new DecimalFormat("#.##");

    protected String LIBRARY_INPUT;// = "#EMPTY";
    protected String CMO_PATIENT_ID;
    protected String SEX; // = "Unknown";
    protected String SPECIMEN_COLLECTION_YEAR; // ="000";
    protected String ONCOTREE_CODE; // = "#EMPTY";
    protected String TISSUE_SITE; // = "na";
    protected String LIBRARY_YIELD; // = "#EMPTY";
    protected String CAPTURE_BAIT_SET; // = "#EMPTY";
    protected String CAPTURE_CONCENTRATION; // = "#EMPTY";
    protected String CAPTURE_INPUT; // = "#EMPTY";
    protected String CAPTURE_NAME; // = "#EMPTY";
    protected String BAIT_VERSION; // = "#EMPTY";
    protected String SPIKE_IN_GENES; // = "na";
    
    /** 
     * This is the method that CreateManifestSheet calls.<br>First it looks at parent class's method, then uses new method "getSpreadOutInfo" and "addPoolNormalDefaults"
     * @see SampleInfo#SampleInfo
     * @see #getSpreadOutInfo
     * @see #addPooledNormalDefaults
     **/
    public SampleInfoImpact(String req, User apiUser, DataRecordManager drm, DataRecord rec, Map<String, Set<String>> SamplesAndRuns, Boolean force, Boolean poolNormal, Boolean transfer,LogWriter l){
        super(req,apiUser,drm,rec,SamplesAndRuns,force,poolNormal,transfer,l);
        getSpreadOutInfo(req, apiUser, drm,rec,  SamplesAndRuns,force, poolNormal,transfer,l);
        if(poolNormal) {
            addPooledNormalDefaults(rec, apiUser, drm, SamplesAndRuns);
        }
    }
   
    /** BLAH **/ 
    protected void addPooledNormalDefaults(DataRecord rec, User apiUser, DataRecordManager drm, Map<String, Set<String>> SamplesAndRuns){
        this.CAPTURE_BAIT_SET= this.ConsensusBaitSet;
        this.SPIKE_IN_GENES= this.ConsensusSpikeIn;
        if(this.SPIKE_IN_GENES != "na" &&  ! this.CAPTURE_BAIT_SET.startsWith("#")){
            this.BAIT_VERSION = this.CAPTURE_BAIT_SET + "+" + this.SPIKE_IN_GENES;
        }else{
            this.BAIT_VERSION = this.CAPTURE_BAIT_SET;
        }
        
        //System.out.println("NAME: " + this.IGO_ID); 
        this.CMO_PATIENT_ID= optionallySetDefault(this.CMO_PATIENT_ID, this.CMO_SAMPLE_ID);
        this.INVESTIGATOR_SAMPLE_ID = optionallySetDefault(this.INVESTIGATOR_SAMPLE_ID, this.CMO_SAMPLE_ID);
        this.INVESTIGATOR_PATIENT_ID = optionallySetDefault(this.INVESTIGATOR_PATIENT_ID, this.CMO_SAMPLE_ID);
        this.ONCOTREE_CODE = optionallySetDefault(this.ONCOTREE_CODE, "Normal");
        this.SPECIMEN_COLLECTION_YEAR = optionallySetDefault(this.SPECIMEN_COLLECTION_YEAR, "000");
        this.SEX="na";
        this.SAMPLE_CLASS=optionallySetDefault(this.SAMPLE_CLASS, "PoolNormal");
        this.SPECIES = optionallySetDefault(this.SPECIES, "POOLNORMAL");
        if(this.CMO_SAMPLE_ID.contains("MOUSE")){
        this.SPECIES = "Mouse";
        }
        this.MANIFEST_SAMPLE_ID = this.CMO_SAMPLE_ID + "_IGO_" + this.BAIT_VERSION + "_" + this.BARCODE_INDEX;
        
        // According to Katenlynd the preservation type of all mouse samples will ALWAYS be Frozen. (2/20/2017)
        if(this.CMO_SAMPLE_ID.toUpperCase().contains("FROZEN") || this.SPECIES.equals("Mouse")){
            this.SPECIMEN_PRESERVATION_TYPE = "Frozen";
        } else if(this.CMO_SAMPLE_ID.toUpperCase().contains("FFPE")){
            this.SPECIMEN_PRESERVATION_TYPE = "FFPE";
        } else{
            this.SPECIMEN_PRESERVATION_TYPE = "Unknown";
        }

        // POOLED NORMALS don't have sample specific QC when searching the first time
        // We need to look at all the runs that were already added to the (samplesAdnRuns, or just runs)
        // Then if the pooled normal has a sample specific qc of that run type, add to include run id! 
        this.INCLUDE_RUN_ID = getPooledNormalRuns(rec, apiUser, drm, SamplesAndRuns);
        print("INCLUDE RUN ID: " + this.INCLUDE_RUN_ID);
    }
   
    protected String getPooledNormalRuns(DataRecord rec, User apiUser, DataRecordManager drm, Map<String, Set<String>> SamplesAndRuns){
        //populating run ids
        Set<String> allRunIds = new HashSet<String>();
        for(Set<String> val : SamplesAndRuns.values()){
            allRunIds.addAll(val);
        }

        System.out.println("Looking at this sample: " + this.IGO_ID);
 
        //System.out.println("ALL RUN IDS: " + StringUtils.join(allRunIds, ";"));

        List<String> goodRunIds= new ArrayList<String>();
 
        // get decendants of sequencing qc type, check to make sure it isn't failed
        // then check to see if it is in the all Rund Ids set
        List<DataRecord> qcRecs = new ArrayList<DataRecord>();
        List<Object> qcValue = new ArrayList<Object>();
        List<Object> qcRunID = new ArrayList<Object>();
        try {
            qcRecs = rec.getDescendantsOfType("SeqAnalysisSampleQC" , apiUser);
            qcValue = drm.getValueList(qcRecs, "SeqQCStatus", apiUser);
            qcRunID = drm.getValueList(qcRecs, "SequencerRunFolder", apiUser);
        } catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }

        // Now try to match everything up
        if(qcRecs == null || qcValue == null || qcRunID == null){
            print("[WARNING] No sample specific qc for ctrl " + this.CMO_SAMPLE_ID + " AKA " + this.IGO_ID + ".");
            return null;
        }

        if(qcRecs.size() == 0 || qcValue.size() == 0 || qcRunID.size() == 0){
            print("[WARNING] No sample specific qc for ctrl " + this.CMO_SAMPLE_ID + " AKA " + this.IGO_ID + ".");
            return null;
        }

        // Go through each one, if it is 1) not "Failed" and 2) in current list of allRunIds
        for(int i=0; i < qcRecs.size(); i++){
            String qcVal = (String) qcValue.get(i);
            if (! qcVal.startsWith("Failed")){
                String runPath = (String) qcRunID.get(i);
                String[] pathList= runPath.split("/");
                String runName = pathList[(pathList.length - 1)];
                String pattern = "^([a-zA-Z]+_[\\d]{4})([a-zA-Z\\d\\-_]+)";
                String shortRunID = runName.replaceAll(pattern, "$1");
                if ( allRunIds.contains(shortRunID)){
                    goodRunIds.add(shortRunID);
                }
            }
        }
        
        if(goodRunIds != null && goodRunIds.size() > 0){
            return StringUtils.join(goodRunIds, ";");
        } 
        return ""; 
    }
    
    protected String optionallySetDefault(String currentVal, String defaultVal){
        if (currentVal == null || currentVal.startsWith("#") || currentVal == "null"){
            return defaultVal;
        }
        return currentVal;
    }
    
    @Override
    protected void grabRequestSpecificValues(Map<String, Object>fieldMap){    //Changes between ReqTypes
        // Sample Class changes depending on what request is being pulled.
        // HOWEVER sometimes the new value is NULL, in which case we want to keep TumorOrNormal
        this.SAMPLE_CLASS = setFromMap(this.SAMPLE_CLASS, "TumorOrNormal", fieldMap);
        this.SAMPLE_CLASS = setFromMap(this.SAMPLE_CLASS, "CMOSampleClass", fieldMap);
        
        // These are just new values needed to track
        this.SPECIMEN_COLLECTION_YEAR = setFromMap(this.SPECIMEN_COLLECTION_YEAR,"CollectionYear", fieldMap);
        this.SEX = setFromMap(this.SEX, "Gender", fieldMap);
        this.CMO_PATIENT_ID = setFromMap(this.CMO_PATIENT_ID, "CmoPatientId", fieldMap);
        this.TISSUE_SITE=setFromMap(this.TISSUE_SITE, "TissueLocation", fieldMap);
        
        if(fieldMap.containsKey("DMPLibraryInput")){
            this.LIBRARY_INPUT = setFromMap(this.LIBRARY_INPUT, "DMPLibraryInput", fieldMap);
            this.LIBRARY_YIELD = setFromMap(this.LIBRARY_YIELD, "DMPLibraryOutput", fieldMap);
        }
        
        // OnocTree Code
        this.ONCOTREE_CODE = optionallySetDefault(this.ONCOTREE_CODE, "#UNKNOWN");
        String tumorLong = setFromMap(this.ONCOTREE_CODE, "TumorType", fieldMap);
        if(tumorLong != "null" && tumorLong.length() > 0 ){ // && !tumorLong.equals("#EMPTY")){
            String[] tumor_type = tumorLong.split("[\\(\\)]");
            if(tumor_type.length == 2) {
                this.ONCOTREE_CODE = tumor_type[1];
            } else if(tumor_type.length == 1 && tumor_type[0].length() > 1){
                this.ONCOTREE_CODE = tumor_type[0];
            } 
            if( this.SAMPLE_CLASS.contains("Normal")) {
            // If sample class is normal override tumor type and set to "na"
                this.ONCOTREE_CODE = "na";
            }
        }else if (this.SAMPLE_CLASS.equals("Normal")){
            this.ONCOTREE_CODE = "na";
        }else{
            if(this.ONCOTREE_CODE.startsWith("#")){
                this.ONCOTREE_CODE = "#UNKNOWN";
            }
        }

        return;
    }
    
    public static HashMap<DataRecord,HashSet<String>> getPooledNormals(){
        return pooledNormals;
    }
    
    @Override
    protected void populateDefaultFields(){
        fieldDefaults.put("SampleId", "#EMPTY");
        fieldDefaults.put("OtherSampleId", "#EMPTY");
        fieldDefaults.put("PatientId", "#EMPTY");
        fieldDefaults.put("UserSampleID", "#EMPTY");
        fieldDefaults.put("SpecimenType", "na");
        fieldDefaults.put("Preservation", "#EMPTY");
        fieldDefaults.put("Species", "#UNKNOWN");
        fieldDefaults.put("CorrectedCMOID", "#EMPTY");
        fieldDefaults.put("RequestId", "#EMPTY");
        fieldDefaults.put("TumorOrNormal", "#EMPTY");
        fieldDefaults.put("CollectionYear", "000");
        
        fieldDefaults.put("CmoPatientId", "#EMPTY");
        fieldDefaults.put("Gender", "Unknown");
        fieldDefaults.put("TissueLocation", "na");
        return;
    }
    
    protected void getSpreadOutInfo(String req, User apiUser, DataRecordManager drm, DataRecord rec, Map<String, Set<String>> SamplesAndRuns, Boolean force, Boolean poolNormal, Boolean transfer,LogWriter l){
        // Spread out information available for IMPACT includes this.BARCODE_ID, this.BARCODE_INDEX
        // this.LIBRARY_YIELD this.LIBRARY_INPUT
        // this.CAPTURE_NAME this.CAPTURE_CONCENTRATION
        // this.CAPTURE_INPUT
        // TODO: Find IGO_ID of pool sample that is between
        
        // This only runs if LIBRARY_INPUT was not given already in CMO Sample Info Data Record
        if(this.LIBRARY_INPUT == null || this.LIBRARY_INPUT.startsWith("#")){
            this.LIBRARY_INPUT = "#EMPTY";
            grabLibInput(drm, rec, apiUser, false, poolNormal);
            if(transfer && this.LIBRARY_INPUT.startsWith("#")){
                grabLibInputFromPrevSamps(drm, rec, apiUser, poolNormal);
            }
            if(this.LIBRARY_INPUT.startsWith("#")){
                print("[WARNING] Unable to find DNA Lib Protocol for Library Input method (sample " + this.CMO_SAMPLE_ID + ")");
                this.LIBRARY_INPUT = "-2";
            }
        }
        
        getBarcodeInfo(drm, rec, apiUser, SamplesAndRuns, transfer, poolNormal, force);
        
        // Capture concentration
        grabCaptureConc(drm, rec, apiUser);
        
        
        if(this.LIBRARY_YIELD == null || this.LIBRARY_YIELD.startsWith("#")){
            this.LIBRARY_YIELD = "#EMPTY";
        }
  
        // DEBUGGING. Print tree. I will recursively go through a sample and print their children data types and then grab the child samples and do it again.
        //printTree(rec, apiUser, 0);

        // If Nimblgen Hybridization was created BEFORE May 5th, ASSUME
        // That Library Yield, and Capture INPUT is correct on Nimblgen data record
        // After that date we MUST get the information from elsewhere
        boolean afterDate =nimbAfterMay5(drm,rec,apiUser, "NimbleGenHybProtocol");
        if(afterDate){
            // First try and get elution volume from Protocol3
            // Otherwise try to get from Protocol 2
            // THEN try to get from 3 using prev samples
            // THEN THEN try to get from 2 using prev samples
            double libVol = getLibraryVolume(drm, rec, apiUser, false, "DNALibraryPrepProtocol3");
            if( libVol <= 0){
                libVol =  getLibraryVolume(drm, rec, apiUser, false, "DNALibraryPrepProtocol2");
            }
            if( libVol <= 0 && transfer){
                libVol = getLibraryVolumeFromPrevSamps(drm, rec, apiUser, "DNALibraryPrepProtocol3");
                if( libVol <= 0){
                    libVol = getLibraryVolumeFromPrevSamps(drm, rec, apiUser, "DNALibraryPrepProtocol2");
                }
            }
            double libConc = getLibraryConcentration(rec, drm, apiUser,transfer, "NimbleGenHybProtocol");
            if( libVol > 0 && libConc > 0 && this.LIBRARY_YIELD.startsWith("#")){
                this.LIBRARY_YIELD = String.valueOf(df.format(libVol * libConc));
            } else if(this.LIBRARY_YIELD.startsWith("#")){
                this.LIBRARY_YIELD = "-2";
            }
            processNimbInfo(drm, rec, apiUser, libVol, libConc, poolNormal, afterDate);
        } else {
            // Here I can just be simple like the good old times and pull stuff from the
            // Nimb protocol
            processNimbInfo(drm, rec, apiUser, -1, -1, poolNormal, afterDate);
        }
        
        
        return;
    }
    
    protected boolean nimbAfterMay5(DataRecordManager drm, DataRecord rec, User apiUser, String protocolName){
        // Here, I am just going to get creation date of this sample. If it is after
        // May 5th, 2016, I return yes. Otherwise return no.
        // This is the only way I can get as much correct information as possible.
        
        Date cutoffDate = new Date();
        List<DataRecord> nimbProtocols = new ArrayList<DataRecord>();
        List<Object> validity = new ArrayList<Object>();
        List<Object> creationDate = new ArrayList<Object>();
        Set<Date> createDates = new HashSet<Date>();
        
        // Grabbing the items I need to see if this is an OK nimb to use
        try{
            cutoffDate = sdf.parse("2016-05-05");
            nimbProtocols = rec.getDescendantsOfType(protocolName , apiUser);
            validity = drm.getValueList(nimbProtocols, "Valid", apiUser);
            creationDate = drm.getValueList(nimbProtocols, "DateCreated", apiUser);
        } catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        
        if(nimbProtocols == null || validity == null || creationDate == null){
            print("[ERROR] Unknown error in checked creation date of" + protocolName + " protocols");
            return false;
        }
        if (nimbProtocols.size() == 0 || validity.size() == 0 || creationDate.size() == 0){
            print("[WARNING] Not able to find " + protocolName + " Protocol or date created");
            return false;
        }
        
        //Iterate, check validity, add createDate to a HashSet of Date
        for(int i = 0; i < nimbProtocols.size(); i++){
            boolean valid = false;
            try{
                valid = (boolean)validity.get(i);
            } catch  (NullPointerException e) {
                valid = false;
            } 
            // I don't care if it is false or null. Only if true.
            if(valid){
                Date thisDate = new Date((long)creationDate.get(i));
                createDates.add(thisDate);
            }
        }
        
        // Check all the dates, if ANY are after may5th return true
        for( Date d : createDates){
            if(d.compareTo(cutoffDate) > 0){
                return true;
            }
        }
        // This means none were after May 5th
        return false;
    }
    
    protected void getBarcodeInfo (DataRecordManager drm, DataRecord rec, User apiUser, Map<String, Set<String>> SamplesAndRuns, Boolean transfer, Boolean poolNormal, Boolean force){
        // This will give us this.BARCODE_ID and this.BARCODE_INDEX
        //Find the Sample Specific QC record for this sample, and go up until you find an index.
        List<DataRecord> qcRecs = new ArrayList<DataRecord>();
        try{
            // First find sample specific qc with this cmo sample id, one of the run IDs,
            qcRecs = drm.queryDataRecords("SeqAnalysisSampleQC", "Request = '" + this.REQUEST_ID + "' AND OtherSampleId = '" + this.CMO_SAMPLE_ID + "'", apiUser);
            
        }catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        // Now I can iterate, the qcRecs, and I have to check to see if the runIDs are the same as above
        if(qcRecs == null || qcRecs.size() == 0){
            if(poolNormal || force ){
                String extraInput = "";
                if ( !poolNormal){
                    extraInput = "_";
                }
                try{
                    qcRecs = drm.queryDataRecords("IndexBarcode", "SampleId LIKE '" + this.IGO_ID + extraInput + "%'", apiUser);
                    if(qcRecs != null && qcRecs.size() > 0){
                        DataRecord bc = qcRecs.get(qcRecs.size() - 1 );
                        this.BARCODE_ID = bc.getStringVal("IndexId", apiUser);
                        this.BARCODE_INDEX = bc.getStringVal("IndexTag", apiUser);
                        return;
                    }
                } catch(Throwable e) {
                    logger.logError(e);
                    e.printStackTrace();
                }
                
            }
            print("[ERROR] Unable to get barcode for " + this.IGO_ID + " AKA: " + this.CMO_SAMPLE_ID ); //" there must be a sample specific QC data record that I can search up from");
            return;
        }
        DataRecord qcToUse = null;
        String[] runParts = null;
        for(DataRecord r : qcRecs){
            try{
                runParts= r.getStringVal("SequencerRunFolder", apiUser).split("_");
            }catch (Throwable e) {
                logger.logError(e);
                e.printStackTrace();
            }
            
            if(runParts.length <2){
                print("[ERROR] sequencingRunFolder incorrectly split by '_', or not correctly pulled.");
            }
            
            String RunID = runParts[0] + "_" + runParts[1];
            if (! SamplesAndRuns.keySet().contains(this.CMO_SAMPLE_ID)){
                print("[ERROR] The sample ID I am using to search for runs is not the correct sample ID.");
                return;
            }
            if (SamplesAndRuns.get(this.CMO_SAMPLE_ID).contains(RunID)){
                // When I find a qc that matches the run ID, I can exit this for loop. I only need one because they should all have (the same) barcode upstream
                qcToUse = r;
                break;
            }
        }
        
        // Now I want to get all the above things that have DR type sample, and look to see if they have children of type Index.
        if(qcToUse != null){
            try{
                List<DataRecord> sampAncestors = qcToUse.getAncestorsOfType("Sample", apiUser);
                List<List<Map<String,Object>>> indexRecsPerSamp = drm.getFieldsForChildrenOfType(sampAncestors, "IndexBarcode", apiUser);
                if(indexRecsPerSamp != null || indexRecsPerSamp.get(0) != null || indexRecsPerSamp.size() != 0){
                    for(int x = 0; x < indexRecsPerSamp.size(); x++){
                        //This Iterates index of the sampancestors results, now you go through each children of type Index barcode child.
                        List<Map<String,Object>> barcodeRecs =indexRecsPerSamp.get(x);
                        if ( barcodeRecs.size() == 0){
                            continue;
                        }
                        // If there are more than one records, I only need the first one
                        // IF there is more than one and they are different, I wouldn't be
                        // able to tell which is the correct one to use anyway.
                        Map<String,Object> fieldMap = barcodeRecs.get(0);
                        this.BARCODE_ID = setFromMap(this.BARCODE_ID,"IndexId",fieldMap);
                        this.BARCODE_INDEX = setFromMap(this.BARCODE_INDEX, "IndexTag", fieldMap);
                        
                        // Now get the sample parent's IGO ID for sequencing
                        // TODO: verify that this is correct the correct place to get it from
                        DataRecord samp =sampAncestors.get(x);
                        this.SEQ_IGO_ID = samp.getStringVal("SampleId", apiUser);
                        
                        break;
                    }
                }
            }catch (Throwable e) {
                logger.logError(e);
                e.printStackTrace();
            }
        }
        return;
    }
    
    /*
     Library Input
     */
    protected void grabLibInputFromPrevSamps(DataRecordManager drm, DataRecord rec, User apiUser, Boolean poolNormal){
        // recursive method that goes up the sample records, looking for ones that have children
        // DNALibraryPrepProtocol1, or KapaAutoNormalizationProtocol
        List<DataRecord> psamp = new ArrayList<DataRecord>();
        try{
            psamp = rec.getParentsOfType("Sample", apiUser);
        }catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        for (DataRecord samp : psamp ){
            try{
                List<DataRecord> DNALibPrep = Arrays.asList(samp.getChildrenOfType("KAPALibPlateSetupProtocol1", apiUser));
                List<DataRecord> KapaAutoNormProt = Arrays.asList(samp.getChildrenOfType("KapaAutoNormalizationProtocol", apiUser));
                if(DNALibPrep.size() > 0 || KapaAutoNormProt.size() > 0){
                    grabLibInput(drm, samp, apiUser, true, poolNormal);
                }
            } catch (Throwable e) {
                logger.logError(e);
                e.printStackTrace();
            }
            if(! this.LIBRARY_INPUT.startsWith("#")){
                return;
            }
            grabLibInputFromPrevSamps(drm, samp ,apiUser, poolNormal);
        }
        
        return;
    }
    
    protected void grabLibInput(DataRecordManager drm, DataRecord rec, User apiUser, Boolean childrenOnly, Boolean poolNormal){
        if(poolNormal){
            this.LIBRARY_INPUT = "0";
            List<DataRecord> DNALibPreps = null;
            try{
                DNALibPreps = drm.queryDataRecords("DNALibraryPrepProtocol1", "SampleId = '" + this.IGO_ID + "'", apiUser);
            }catch(Throwable e) {
                e.printStackTrace();
            }
            if(DNALibPreps != null && DNALibPreps.size() > 0){
                DataRecord DNALP = DNALibPreps.get(DNALibPreps.size() - 1);
                try{
                    this.LIBRARY_INPUT = String.valueOf(df.format(DNALP.getDoubleVal("TargetMassAliq1", apiUser)));
                }catch(Throwable e) {
                    e.printStackTrace();
                }
            }
            return;
        }
        Boolean Normalization_DR = false;
        List<DataRecord> KANP = new ArrayList<DataRecord>();
        try{
            // First check KAPALibPlateSetupProtocol1 (newer data record..)
            // If that is empty try to find the old one
            if(childrenOnly){
                KANP = Arrays.asList(rec.getChildrenOfType("KAPALibPlateSetupProtocol1", apiUser));
                if(KANP == null || KANP.size() == 0 ){
                    KANP = Arrays.asList(rec.getChildrenOfType("KapaAutoNormalizationProtocol", apiUser));
                    if(KANP == null || KANP.size() == 0 ){
                        return;
                    }
                    Normalization_DR = true;
                }
            }else{
                KANP = rec.getDescendantsOfType("KAPALibPlateSetupProtocol1", apiUser);
                if(KANP == null || KANP.size() == 0 ){
                    KANP = rec.getDescendantsOfType("KapaAutoNormalizationProtocol", apiUser);
                    if(KANP == null || KANP.size() == 0 ){
                        return;
                    }
                    Normalization_DR = true;
                }
            }
        }catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        if(KANP != null && KANP.size() > 0 ){
            int largestValidIndex = -1;
            if (! Normalization_DR){
                // for each item in KANP, check valid box. 
                // if not true, add index to list to remove from list. 
                List<Object> validity = new ArrayList<Object>();
                try{
                    validity = drm.getValueList(KANP, "Preservation", apiUser); 
                } catch (Throwable e ){
                    e.printStackTrace();
                }

                if(validity != null && validity.size() > 0){
                    for(int x = 0; x < validity.size(); x++){
                        try{
                            if(! ((String) validity.get(x)).isEmpty() && ((String) validity.get(x)) != null){
                               largestValidIndex = x;
                           }
                        } catch (NullPointerException e){
                        }
                    }
                }
           
                // if none are valid, make this.LIBRARY_INPUT == #empty
                if (largestValidIndex < 0){
                    this.LIBRARY_INPUT = "#EMPTY";
                    return;
                }
            }else {
                largestValidIndex = KANP.size() -1;
            }

            // As per Agnes who talked to Mike Berger, If there are multiple Kapa protocols,
            // Simply pick the LAST one that is in the list. list.size() - 1
            String val = "";
            try{
                if(Normalization_DR){
                    val = String.valueOf(df.format(KANP.get(largestValidIndex).getDoubleVal("Aliq2TargetMass", apiUser)));
                } else {
                    val = String.valueOf(df.format(KANP.get(largestValidIndex).getDoubleVal("TargetMassAliq1", apiUser)));
                }
            } catch(Throwable e){
                e.printStackTrace();
            }
            if(val != null && Double.parseDouble(val) > 0){
                this.LIBRARY_INPUT = val;
            } else{
                this.LIBRARY_INPUT = "#EMPTY";
                //System.out.println("Bad val: " + val);
            }
        }
        return;
    }
    
    /*
     Library Volume
     */
    protected double getLibraryVolume(DataRecordManager drm, DataRecord rec, User apiUser, Boolean childOnly, String dataRecordName){
        // Th/is gets the elution volume
        double input = -1;
        List<DataRecord> DNALibPreps = new ArrayList<DataRecord>();
        try {
            if(childOnly){
                DNALibPreps = Arrays.asList(rec.getChildrenOfType(dataRecordName, apiUser));
            }else{
                DNALibPreps = rec.getDescendantsOfType(dataRecordName, apiUser);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        if(DNALibPreps == null || DNALibPreps.size() == 0 ){
            //print("NULL OR SIZE OF ZERO! " + dataRecordName);
            return -9;
        }
        for(DataRecord n1 : DNALibPreps){
            // Must check for "Valid" checkbox because some are not valid.
            Boolean real = true;
            try{
                real = n1.getBooleanVal("Valid", apiUser);
            } catch(Throwable e){
                real=false;
            }
            if (real) {
                try{
                    input = n1.getDoubleVal("ElutionVol", apiUser);
                } catch (NullPointerException e){
                    input=-1;
                    print("[ERROR] Cannot find elution vol for " + this.CMO_SAMPLE_ID + " AKA " + this.IGO_ID + " Using DataRecord " + dataRecordName);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        return input;
    }
    
    protected double getLibraryVolumeFromPrevSamps(DataRecordManager drm, DataRecord rec, User apiUser, String dataRecordName){
        double result = -1;
        List<DataRecord> psamp = new ArrayList<DataRecord>();
        
        try{
            psamp = rec.getParentsOfType("Sample", apiUser);
        }catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        
        for (DataRecord samp : psamp ){
            result = getLibraryVolume(drm, samp, apiUser, true, dataRecordName);
            if(result > 0){
                return result;
            }
            result = getLibraryVolumeFromPrevSamps(drm, samp,apiUser, dataRecordName);
        }
        return result;
    }
    
    /*
     Library Concentration
     */

    protected double getLibraryConcentration(DataRecord rec, DataRecordManager drm, User apiUser, Boolean transfer, String alternativeDR) {
        // If there is a DNALibParent present (VIA get LibVolume)
        DataRecord libConcParent = null;
        
        String concentration = "-1";
        double conc_ng=-1;
        
        // Don't use DNA Library Prep Protocl anymore. Find alternativeDR, and use that to find the parent
        // sample type

        List<DataRecord> grandparents = new ArrayList<DataRecord>();
        try{
            List<DataRecord> altDrs = rec.getDescendantsOfType(alternativeDR, apiUser);
            if (altDrs != null && altDrs.size() > 0){
                List<Object> validity = drm.getValueList(altDrs, "Valid", apiUser);
                List<Object> igoId = drm.getValueList(altDrs, "SampleId", apiUser);
                if(validity != null && validity.size() > 0){
                    for(int x = 0; x < validity.size(); x++){
                        try{
                            if( ((boolean) validity.get(x)) && ((String) igoId.get(x)).contains(this.IGO_ID) ){
                                DataRecord chosenAltDr = altDrs.get(x);
                                List<DataRecord> parents = chosenAltDr.getParentsOfType("Sample", apiUser);
                                if(parents != null && parents.size() > 0){
                                    libConcParent = parents.get(0);
                                    if(transfer){
                                        grandparents = libConcParent.getParentsOfType("Sample", apiUser);
                                    }
                                }
                            }
                        } catch (NullPointerException e ) {
                        }
                    }
                }
            }
        } catch(Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        
        if(libConcParent == null){
            print("[WARNING] Unable to find lib concentration for " + this.IGO_ID);
            return -9;
        }

        // Pull concentration values in libConcParent if this is okay.
        conc_ng = pullConcValues(rec, drm, apiUser, libConcParent);

        // IF conc_ng is less than 0, check to see if the grandparent datarecord has a different 
        // request. ONLY THEN redo pullConcValues, but with grandparent sample
        if(conc_ng <= 0 && grandparents != null && grandparents.size() > 0){
            DataRecord gp = grandparents.get(0);
            String request = this.REQUEST_ID;
            try{
                request = gp.getStringVal("RequestId", apiUser);
            } catch(Throwable e) {
            logger.logError(e);
            e.printStackTrace();
            }

            if(! request.equals(this.REQUEST_ID)){
                conc_ng = pullConcValues(rec, drm, apiUser, gp);
            }
        }

        return conc_ng;

    }

    protected double pullConcValues (DataRecord rec, DataRecordManager drm, User apiUser, DataRecord libConcParent){
        String concentration = "-1";
        double conc_ng=-1;
        List<DataRecord> DLP = new ArrayList<DataRecord>();
        DLP.add(libConcParent);
        List<Map<String,Object>> concRecs = new ArrayList<Map<String,Object>>();


        // First try to look up MolarConcentrationAssignment
        // If not present look up QCDatum ** more rules with QCDatum below
        try{
            concRecs = drm.getFieldsForChildrenOfType(DLP, "MolarConcentrationAssignment", apiUser).get(0);
        } catch(Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        // If there are Molar Concentratin Assignments
        if(concRecs != null || concRecs.size() > 0){
            for(Map<String,Object> ma: concRecs){
                // Only look at concentrations that are ng/ul
                String concUnits = (String) ma.get("ConcentrationUnits");
                if(! (concUnits.toLowerCase()).equals("ng/ul")){
                    continue;
                }
                concentration = setFromMap("-1", "Concentration", ma);
                if(concentration != null && Double.parseDouble(concentration) > 0){
                    conc_ng = Double.parseDouble(concentration);
                    // only need the first available concentration (as of now).
                    return conc_ng;
                }
            }
        }
        // If this doesn't work, try using QC Datum
        if(conc_ng <= 0){
            try{
                concRecs = drm.getFieldsForChildrenOfType(DLP, "QCDatum", apiUser).get(0);
            } catch (Throwable e) {
                logger.logError(e);
                e.printStackTrace();
            }
            for(Map<String,Object> n2 : concRecs ) {
                String type = (String) n2.get("DatumType");
                //DO NOT grab from tapestation or bioanalyzer. They are not accurate (as per Kate).
                if(type.startsWith("TapeStation") || type.startsWith("Bioanalyzer")){
                    continue;
                } else {
                    if (!type.startsWith("Qubit")){
                        print("[INFO] This qc datum is type " + type + ", I will try to pull concentration from it.");
                    }
                    concentration = setFromMap("-1", "CalculatedConcentration", n2) ;
                    if(concentration != null && Double.parseDouble(concentration) > 0){
                        conc_ng = Double.parseDouble(concentration);
                        // only need the first available concentration (as of now).
                        return conc_ng;
                    }
                }
            }
        }
        return conc_ng;
    }
    
    
    /*
     Nimblgen Pulling
     */
    private void processNimbInfo(DataRecordManager drm, DataRecord rec, User apiUser, double libVol, double libConc, boolean poolNormal, boolean afterDate){
        // This should set Lib Yield, Capt Input, Capt Name, Bait Set, Spike ins
        List<DataRecord> nimbProtocols = new ArrayList<DataRecord>();
        List<Object> valid = new ArrayList<Object>();
        List<Object> poolName = new ArrayList<Object>();
        List<Object> igoId = new ArrayList<Object>();
        Map<String,Object> nimbRec = new HashMap<String,Object>();
        // If libVol & libConc not null or negative make LibYield
        if(libVol > 0 && libConc > 0 && this.LIBRARY_YIELD.startsWith("#")){
            this.LIBRARY_YIELD=String.valueOf(df.format(libVol * libConc));
        }
        
        // get all nimb protocols from this sample record
        // Grab valid and pool name first to find a good record. Then grab all
        // Fields from the good record.
        try{
            nimbProtocols = rec.getDescendantsOfType("NimbleGenHybProtocol", apiUser);
            valid = drm.getValueList(nimbProtocols, "Valid", apiUser);
            poolName = drm.getValueList(nimbProtocols, "Protocol2Sample", apiUser);
            igoId =drm.getValueList(nimbProtocols, "SampleId", apiUser);
        } catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        
        DataRecord chosenRec=null;
        // Look for issues
        if(nimbProtocols == null && nimbProtocols.size() == 0){
            print("[ERROR] No NoNymbHybProtocol DataRecord found for " + this.CMO_SAMPLE_ID + "(" + this.IGO_ID + "). The baitset/spikin, Capture Name, Capture Input, Library Yield will be unavailable. ");
            return;
        }
        // only one sample (rec) that was checked for
        // doesn't break out of this because I want to pick the LAST valid one.

        // First check for valid, containing a child sample pool name, and THIS.IGO_ID is found in SampleId
        for(int a = 0; a < nimbProtocols.size(); a++){
            Boolean validity = false;
            try{ 
                validity = (boolean) valid.get(a);
            } catch (NullPointerException e ) {
                validity = false;
            }
            String igoIdGiven = (String) igoId.get(a);
            if(! igoIdGiven.contains(this.IGO_ID)){
                print("[WARNING] Nimblegen D.R. has a different igo id than this sample: Nimb: " + igoIdGiven + ", this sample: " + this.IGO_ID);
                continue;
            }
            String pool = (String) poolName.get(a);
            //System.out.println("Pool name : " + pool + " Valid? " + validity);
            if(validity && !pool.isEmpty() && ! pool.equals("null")) {
                if(poolNormal){ 
                    if (poolNameList.contains(pool)){
                        //System.out.println("CHOSEN POOL: " + pool);
                        chosenRec = nimbProtocols.get(a);
                        break;
                    }
                } else {
                    chosenRec = nimbProtocols.get(a);
                    poolNameList.add(pool);
                }
            }
        }
        // check again
        if(chosenRec == null){
            for(int a = 0; a < nimbProtocols.size(); a++){
                Boolean validity = false;
                try{
                    validity = (boolean) valid.get(a);
                } catch (NullPointerException e ) {
                    validity = false;
                }
                if(validity ){
                    chosenRec = nimbProtocols.get(a);
                    break;
                }
            }
            if(chosenRec == null){
                print("[ERROR] No VALID NimblgenHybridizationProtocol DataRecord found for " + this.CMO_SAMPLE_ID +  "(" + this.IGO_ID + "). " + poolNameList + " The baitset/spikin, Capture Name, Capture Input, Library Yield will be unavailable. ");
            return;}
        }
        
        populatePoolNormals(chosenRec, apiUser);
        
        try{
            nimbRec = chosenRec.getFields(apiUser);
        } catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        // another check
        if(nimbRec == null || nimbRec.size() == 0){
            print("[ERROR] Unknown error while pulling getFields");
            return;
        }
        
        //Deal with if lib yield is not filled out
        if(this.LIBRARY_YIELD.startsWith("#") && ! afterDate){
            if( libVol <=0 ){
                // now I have to get the vol from the data record
                double vol = Double.parseDouble(setFromMap("-1", "StartingVolume", nimbRec));
                double conc = Double.parseDouble( setFromMap("-1", "StartingConcentration", nimbRec));
                if(vol > 0 && conc > 0 ){
                    this.LIBRARY_YIELD = String.valueOf(df.format(vol * conc));
                }
            }else{
                //Only need concentration
                double conc = Double.parseDouble(setFromMap("-1", "StartingConcentration", nimbRec));
                if(conc > 0 ){
                    this.LIBRARY_YIELD = String.valueOf(df.format(libVol * conc));
                }
            }
            // for ease, just grab the other thing besides voltoUse
            this.CAPTURE_INPUT = setFromMap("#EMPTY", "SourceMassToUse", nimbRec);
        }
        if(libConc > 0){
            double volumetoUse = Double.parseDouble(setFromMap("-1", "VolumeToUse", nimbRec));
            if( volumetoUse > 0 ){
                this.CAPTURE_INPUT = String.valueOf(Math.round(libConc * volumetoUse));
            }
        } else {
            this.CAPTURE_INPUT = "-2";
        }
        // Now the rest of the stuff
        this.CAPTURE_NAME=setFromMap("#EMPTY","Protocol2Sample", nimbRec);
        this.CAPTURE_BAIT_SET = setFromMap("#EMPTY", "Recipe", nimbRec);
        this.SPIKE_IN_GENES=setFromMap("na", "SpikeInGenes", nimbRec);
        
        if(this.SPIKE_IN_GENES != "na" &&  ! this.CAPTURE_BAIT_SET.startsWith("#")){
            this.BAIT_VERSION = this.CAPTURE_BAIT_SET + "+" + this.SPIKE_IN_GENES;
        }else{
            this.BAIT_VERSION = this.CAPTURE_BAIT_SET;
        }
        
        if(! poolNormal){
            ConsensusBaitSet = this.CAPTURE_BAIT_SET;
            ConsensusSpikeIn = this.SPIKE_IN_GENES;
        }
        return;
    }
    
    private void populatePoolNormals(DataRecord Nymb1, User apiUser) {
        // add pooled normal to hashmap
        try {
            DataRecord temp = Nymb1.getParentsOfType("Sample", apiUser).get(0);
            List<DataRecord> NymbResult = Arrays.asList(temp.getChildrenOfType("Sample", apiUser));
            for (DataRecord poolSamp : NymbResult){
                // HERE check tos ee if it was added ot a flowcell?
                List<DataRecord> lanes = poolSamp.getDescendantsOfType("FlowCellLane", apiUser);
                if(lanes == null || lanes.size() == 0 ){
                    continue;
                }
                String name = poolSamp.getStringVal("SampleId", apiUser);
                List <DataRecord> PoolParents = poolSamp.getParentsOfType("Sample", apiUser);
                for (DataRecord rent : PoolParents) {
                    if(rent.getStringVal("SampleId", apiUser).startsWith("CTRL") ){
                        HashSet<String> tempSet = new HashSet<String>();
                        
                        if(pooledNormals == null ){
                            pooledNormals = new HashMap<DataRecord,HashSet<String>>();
                        }
                        if (pooledNormals.containsKey(rent)){
                            tempSet.addAll(pooledNormals.get(rent));
                        }
                        tempSet.add(poolSamp.getStringVal("SampleId", apiUser));
                        //System.out.println("SAMPLE ID: " + this.CMO_SAMPLE_ID + " + " + this.IGO_ID);
                        //System.out.println("FOUND: " + rent.getStringVal("SampleId", apiUser) + "\nPOOL: " + name);
                        pooledNormals.put(rent, tempSet);
                        
                    }
                }
            }
        } catch (Throwable e) {
            logger.logError(e);
        }
        return;
    }
    
    /*
     Capture Concentration
     */
    protected void grabCaptureConc(DataRecordManager drm, DataRecord rec, User apiUser){
        // Since soon we won't have any way to find out if this was from a passing
        // pool that was sequenced, I'm just going to assume it is.
        List<DataRecord> captureRecords = new ArrayList<DataRecord>();
        try{
            captureRecords = rec.getDescendantsOfType("NormalizationPooledLibProtocol", apiUser);
            if(captureRecords != null && captureRecords.size() != 0){
                for(DataRecord CaptureInfo : captureRecords){
                    //DataRecord CaptureInfo = captureRecords.get(captureRecords.size()-1);
                    double captConc = 0;
                    try{
                        captConc = CaptureInfo.getDoubleVal("InputLibMolarity", apiUser);
                    } catch (NullPointerException e) {
                        captConc=0;
                    }
                    if ( captConc > 0 ){
                        this.CAPTURE_CONCENTRATION = String.valueOf(captConc);
                    }else{
                        this.CAPTURE_CONCENTRATION = "#EMPTY";
                    }
                    
                    String name = CaptureInfo.getStringVal("SampleId", apiUser);
                    if(name != null && name.length() > 0 ){
                        this.CAPTURE_NAME=name;
                    }
                }
            }
        } catch (Throwable e) {
            logger.logError(e);
            e.printStackTrace();
        }
        
    }
 
    protected void printTree(DataRecord rec, User apiUser, int step){
        ArrayList<DataRecord> children = new ArrayList<DataRecord>();;
        try{
            for(int i=0; i <= step*4; i++){
                System.out.print(" ");
            }
            String drName = rec.getDataTypeName();
            System.out.println("name: " + drName);
            children = new ArrayList<DataRecord>(Arrays.asList(rec.getChildren(apiUser)));

        } catch (Throwable e){
            e.printStackTrace();
        }
        for(DataRecord dr : children){
            printTree(dr, apiUser, (step + 1));
        }
    }
    
    /*
     PUBLIC
     */
    public static HashMap<String, String> getSampleRenames(){
        return sampleRenames;
    }
    
    public static ArrayList<String> getLogMessages(){
        ArrayList<String>messagesToReturn = new ArrayList<String>(log_messages);
        return messagesToReturn;
    }
    
    public static Boolean exitLater(){
        return exitLater;
    }
    
    @Override
    public LinkedHashMap<String,String> SendInfoToMap(){
        LinkedHashMap<String, String> myMap = new LinkedHashMap<String, String>();
        myMap.put("IGO_ID", this.IGO_ID);
        myMap.put("EXCLUDE_RUN_ID", this.EXCLUDE_RUN_ID);
        myMap.put("INCLUDE_RUN_ID", this.INCLUDE_RUN_ID);
        myMap.put("INVESTIGATOR_PATIENT_ID", this.INVESTIGATOR_PATIENT_ID);
        myMap.put("INVESTIGATOR_SAMPLE_ID", this.INVESTIGATOR_SAMPLE_ID);
        myMap.put("SAMPLE_CLASS", this.SAMPLE_CLASS);
        myMap.put("SAMPLE_TYPE", this.SAMPLE_TYPE);
        myMap.put("SPECIMEN_PRESERVATION_TYPE", this.SPECIMEN_PRESERVATION_TYPE);
        myMap.put("SPECIES", this.SPECIES);
        myMap.put("STATUS", this.STATUS);
        myMap.put("MANIFEST_SAMPLE_ID", this.MANIFEST_SAMPLE_ID);
        //System.out.println("SAMPLE_ID: " + this.MANIFEST_SAMPLE_ID);
        //System.out.println("IGO ID: " + this.IGO_ID);
        myMap.put("CORRECTED_CMO_ID", this.CORRECTED_CMO_ID);
        myMap.put("SEQ_IGO_ID", this.SEQ_IGO_ID);
        myMap.put("BARCODE_ID", this.BARCODE_ID);
        myMap.put("BARCODE_INDEX", this.BARCODE_INDEX);
        myMap.put("CMO_SAMPLE_ID", this.CMO_SAMPLE_ID);
        myMap.put("LIBRARY_INPUT", this.LIBRARY_INPUT);
        myMap.put("SPECIMEN_COLLECTION_YEAR", this.SPECIMEN_COLLECTION_YEAR);
        myMap.put("ONCOTREE_CODE", this.ONCOTREE_CODE);
        myMap.put("TISSUE_SITE", this.TISSUE_SITE);
        myMap.put("SEX", this.SEX);
        myMap.put("LIBRARY_YIELD", this.LIBRARY_YIELD);
        //System.out.println("LIB YIELD: " + this.LIBRARY_YIELD);
        myMap.put("CMO_PATIENT_ID", this.CMO_PATIENT_ID);
        myMap.put("CAPTURE_NAME", this.CAPTURE_NAME);
        myMap.put("CAPTURE_INPUT", this.CAPTURE_INPUT);
        myMap.put("CAPTURE_CONCENTRATION", this.CAPTURE_CONCENTRATION);
        myMap.put("BAIT_VERSION", this.BAIT_VERSION);
        myMap.put("CAPTURE_BAIT_SET", this.CAPTURE_BAIT_SET);
        myMap.put("SPIKE_IN_GENES", this.SPIKE_IN_GENES);
        
        return myMap;
    }
    
    
    
    
    
    
    
    
    
    
    
    
}
