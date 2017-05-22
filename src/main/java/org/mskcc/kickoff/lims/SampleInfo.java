package org.mskcc.kickoff.lims;

import java.rmi.RemoteException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.text.DecimalFormat;
import java.lang.reflect.Field;
import org.apache.commons.lang3.StringUtils;
import com.velox.api.datarecord.*;
import com.velox.api.user.User;
import com.velox.util.LogWriter;
import org.mskcc.kickoff.util.Constants;

/**
 * SampleInfo.java
 *
 * Purpose: This program will take the data record of type sample, and pull all sample information
 *          needed from the LIMS for the bare bones project files (rnaseq or other)
 *
 * @author Krista Kazmierkiewicz
 */
class SampleInfo
{
    // Keeping a list of all the fields seems like a good idea
    private static final List <String> base_fields = new ArrayList<>(Arrays.asList("IGO_ID", "EXCLUDE_RUN_ID", "INCLUDE_RUN_ID", "INVESTIGATOR_PATIENT_ID", "INVESTIGATOR_SAMPLE_ID", "SAMPLE_CLASS", "SAMPLE_TYPE", "SPECIMEN_PRESERVATION_TYPE", "SPECIES", "STATUS", "MANIFEST_SAMPLE_ID", "CORRECTED_CMO_ID", "CMO_SAMPLE_ID"));

    /** List of xenograft acceptable sample types. Used to change flag xenograftProject **/
    private static final List<String> xenograftClasses = Arrays.asList("PDX", "Xenograft", "XenograftDerivedCellLine");
    /** Flag in order to signify that this request contains xenografts. When a request contains xenografts, it becomes a xenograft request **/
    private static Boolean xenograftProject = false;
    /** Map of field defualts that were decided based on what the validator would accept **/ 
    static final HashMap<String, String> fieldDefaults = new HashMap<>();

    /** Sample renames (old name -> new name) Old name is the name found on the fastq, new name is the corrected CMO Sample ID **/
    static final HashMap<String, String> sampleRenames = new HashMap<>();

    /** If there is an error during this step, we want the CreateManifestSheet instance to know that it needs to exit unsucessfully **/
    static Boolean exitLater = false;
    /** Not sure if this is where the log messages go, or if this is a place holder because the program wouldn't work without it **/
    static final HashSet<String> log_messages = new HashSet<>();
    /** yyyy-MM-dd **/
    final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
 
    /** Placeholder because the script won't work without it :( **/
    final LogWriter logger;
     
    String IGO_ID = Constants.EMPTY;
    String SEQ_IGO_ID = Constants.EMPTY;
    String BARCODE_ID = Constants.EMPTY;
    String CMO_SAMPLE_ID;
    String BARCODE_INDEX = Constants.EMPTY;
    final String EXCLUDE_RUN_ID = "";
    String INCLUDE_RUN_ID = "";
    String INVESTIGATOR_PATIENT_ID;
    String INVESTIGATOR_SAMPLE_ID;
    String SAMPLE_CLASS;
    String SAMPLE_TYPE;
    String SPECIMEN_PRESERVATION_TYPE;
    String MANIFEST_SAMPLE_ID = Constants.EMPTY;
    private final List<String> valid_fields = new ArrayList<>();
    String SPECIES;
    final String STATUS = "";
    String CORRECTED_CMO_ID;
    String REQUEST_ID = Constants.EMPTY;

    /** This is the method that is called by CreateManifestSheet. <p>Features Of this Method:<p> - Populate the default fields<br> - Assign all possible
     * information based on Sample Data record (rec)<br> - Check Sample Level CMO Info Data records to see if there are any fields present there. **NOTE**
     * All fields of CMO Sample Level Info supersede sample data record fields<br> - Check to see if project is Xenograft and flag accordingly<br> - Add to
     * sample renames if necessary<br> - Check for genetically modified data record (*** More things need to be done if this is found, but have not yet been done***)
     **/ 
    public SampleInfo(String req, User apiUser, DataRecordManager drm, DataRecord rec, Map<String, Set<String>> SamplesAndRuns, Boolean force, Boolean poolNormal, Boolean transfer,LogWriter l){
        // Save logger
        this.logger = l;
        this.valid_fields.addAll(base_fields);

        populateDefaultFields();

        List<List<Map<String,Object>>> CMOInfoMap = null;
        Map<String, Object> fieldMap = null;
        try{
            // Make field map of all Sample fields
            fieldMap = rec.getFields(apiUser);
        } catch (RemoteException ignored){
        } catch (Throwable e) {
            e.printStackTrace();
        }
        // First assign everything from Sample data record
        assignValuesFromMap(fieldMap);

        try{
            // Also get all CMO Sample Info data record info.
            List<DataRecord> drList = new ArrayList<>();
            drList.add(rec);
            CMOInfoMap = drm.getFieldsForChildrenOfType(drList,"SampleCMOInfoRecords", apiUser);
            if(transfer && CMOInfoMap.get(0).size() == 0) {
                print("[WARNING] Checking the parent samples of " + this.IGO_ID + " for SampleCMOInfoRecords.");
                // Find all ancestors of type sample
                // For each look for children for type Sample CMO Info Records
                List<DataRecord> parentSamples = rec.getAncestorsOfType("Sample", apiUser);
                for(DataRecord parSam : parentSamples){
                    List<DataRecord> cmoI = Arrays.asList(parSam.getChildrenOfType("SampleCMOInfoRecords", apiUser));
                    if(cmoI.size() > 0 ){
                        drList.clear();
                        drList.add(parSam);
                        CMOInfoMap =  drm.getFieldsForChildrenOfType(drList,"SampleCMOInfoRecords", apiUser);
                        if(CMOInfoMap != null && CMOInfoMap.get(0) != null && CMOInfoMap.get(0).size() != 0){
                            break;
                        }
                    }
                } 
            }
        } catch (RemoteException ignored){
        } catch (Throwable e) {
            e.printStackTrace();
        }
        
        //Then Automatically overwrite whatever is in the values with what is in
        //CMO info data type
        if(CMOInfoMap != null && CMOInfoMap.get(0) != null && CMOInfoMap.get(0).size() != 0){
            List<Map<String,Object>> cmoInfoMap = CMOInfoMap.get(0);
            if(cmoInfoMap.size() > 1)
                print("[WARNING] More than one CMO Sample Info Data record for " + this.IGO_ID);
            else{
                assignValuesFromMap(cmoInfoMap.get(0));
            }
        }else if(!poolNormal) {
            print("[WARNING] Cannot find cmo sample info data record for " + this.IGO_ID);
        }
        
        // Check to see if final SAMPLE_TYPE is found in the xenograft, if so change boolean xenograft to true.
        // This will be used in downstream processing (mostly checking species types of samples AND sometimes figuring out bait version
        if(xenograftClasses.contains(this.SAMPLE_TYPE)){
        	xenograftProject = true;
        }
        
        // add a sample rename
        if(! this.CORRECTED_CMO_ID.isEmpty() && ! this.CORRECTED_CMO_ID.equals(this.CMO_SAMPLE_ID) && ! this.CORRECTED_CMO_ID.startsWith("#")){
            sampleRenames.put(this.CMO_SAMPLE_ID, this.CORRECTED_CMO_ID);
        }
        
        // Stripping IGO ID. A lot of times the sample sheet has samples stripped down to the "first" sample IGO ID.
        // I save this just in case
        String pattern = "^([\\d]{5}_[A-Z]*[_]?[\\d]+)(_?.*)";
        String strippedIGO = this.IGO_ID.replaceAll(pattern, "$1");
        
        
        // Manifest sample ID must match the fastq sample name
        this.CMO_SAMPLE_ID=setFromMap(this.CMO_SAMPLE_ID, "OtherSampleId",fieldMap);
        this.MANIFEST_SAMPLE_ID=this.CMO_SAMPLE_ID + "_IGO_" + strippedIGO;
        // Check for gen modified
        if(this.SPECIES.equals("Mouse")){
            checkForMouseGenModified(rec, apiUser, drm, transfer);
        } 
        
        // Include RUN ID includes all runs that passed. Exclude RUN ID is just a place holder as of now.
        if (!force){
            this.INCLUDE_RUN_ID = StringUtils.join(SamplesAndRuns.get(this.CMO_SAMPLE_ID), ";");
        } else {
            this.INCLUDE_RUN_ID = "#FORCED";
        }
    }

    /** Assigns default values for hash map keys that are necessary for this request type **/
    void populateDefaultFields(){
        fieldDefaults.put("SampleId", Constants.EMPTY);
        fieldDefaults.put("OtherSampleId", Constants.EMPTY);
        fieldDefaults.put("PatientId", Constants.EMPTY);
        fieldDefaults.put("UserSampleID", Constants.EMPTY);
        fieldDefaults.put("SpecimenType", "na");
        fieldDefaults.put("Preservation", Constants.EMPTY);
        fieldDefaults.put("Species", "#UNKNOWN");
        fieldDefaults.put("CorrectedCMOID", Constants.EMPTY);
        fieldDefaults.put("RequestId", Constants.EMPTY);
        fieldDefaults.put("TumorOrNormal", Constants.EMPTY);
        fieldDefaults.put("CollectionYear", "000");
    }
    
    /** This will take all fields from LIMS data records and assign them to hashmap using setFromMap function it then goes to grabRequestSpecificValues method, which doesn't
     * do anything in this class, but is useful for future classes. 
     **/
    private void assignValuesFromMap(Map<String, Object>fieldMap){
        // This will take all Acceptable values from the sample data records, and save them
        
        // REQ ID: Because the CMO Sample Level info will have ONE request ID stored, and
        // Is not necessarily the correct one for THIS request, ignore if the class field is not emtpy.
        if(this.REQUEST_ID.isEmpty() || Objects.equals(this.REQUEST_ID, Constants.EMPTY)){
            this.REQUEST_ID = setFromMap(this.REQUEST_ID, "RequestId", fieldMap);
        }
        // IGO ID: Because the CMO Sample Level info will have an IGO ID stored, and
        // Is not necessarily the correct one for THIS request, ignore if the class field is not emtpy.
        if(this.IGO_ID.isEmpty() || Objects.equals(this.IGO_ID, Constants.EMPTY)){
            this.IGO_ID = setFromMap(this.IGO_ID, "SampleId", fieldMap);
        }
        String OLD_sampleID = this.CMO_SAMPLE_ID;
        this.CMO_SAMPLE_ID = setFromMap(this.CMO_SAMPLE_ID, "OtherSampleId", fieldMap);
        this.SAMPLE_TYPE = setFromMap(this.SAMPLE_TYPE, "SpecimenType", fieldMap);
        this.INVESTIGATOR_SAMPLE_ID = setFromMap(this.INVESTIGATOR_SAMPLE_ID, "UserSampleID", fieldMap);
        this.INVESTIGATOR_PATIENT_ID = setFromMap(this.INVESTIGATOR_PATIENT_ID,"PatientId", fieldMap);
        this.SPECIMEN_PRESERVATION_TYPE = setFromMap(this.SPECIMEN_PRESERVATION_TYPE,"Preservation", fieldMap);
        this.SPECIES = setFromMap(this.SPECIES, "Species", fieldMap);
       
        // If the CMO Sample ID is different between sample data record and  
        if( OLD_sampleID != null && !OLD_sampleID.startsWith("#") && this.CMO_SAMPLE_ID != null && !this.CMO_SAMPLE_ID.startsWith("#") && !OLD_sampleID.equals(this.CMO_SAMPLE_ID)){
            //print("[ERROR] CMO_SAMPLE_ID differs between Sample record and Sample Level CMO Info recrod: Sample: " + OLD_sampleID + " , CMOInfo: " + this.CMO_SAMPLE_ID);
            this.CMO_SAMPLE_ID = OLD_sampleID;
        } 
        
        // This one only works if the field map is Sample Level CMO Info data record
        this.CORRECTED_CMO_ID = setFromMap(this.CMO_SAMPLE_ID, "CorrectedCMOID", fieldMap);

        grabRequestSpecificValues(fieldMap);

    }

    /** Search downstream to find "mouse genetically modified" data type. As of right now this method doesn't do anything. I need to figure out exactly:<br>
     * - If the info in the gen mod is a gene that should be added to the genome<br> - if so, I have to find a way to get the information to Mono
     **/
    private void checkForMouseGenModified(DataRecord rec, User apiUser, DataRecordManager drm, Boolean transfer){
        List<DataRecord> genModList = new ArrayList<>();
        try{
            genModList = rec.getDescendantsOfType("MouseGeneticModification", apiUser);
        } catch (RemoteException ignored){
        } catch (Throwable e) {
            e.printStackTrace();
        }

        if(genModList != null && genModList.size() > 0){
            print("[WARNING] Sample " + this.IGO_ID + " contains a 'mouse genetic modified' data record! We need to figure out if we need to create a new genome");
        } else if (transfer){
            List<DataRecord> sampleAncestors;
            List<List<DataRecord>> genModList2 = null;
            try{
                sampleAncestors = rec.getAncestorsOfType("Sample", apiUser);
                genModList2 = drm.getChildrenOfType(sampleAncestors, "MouseGeneticModification", apiUser);
            } catch (RemoteException ignored){
            } catch (Throwable e) {
                e.printStackTrace();
            }
        

            if(genModList2 != null && genModList2.size() > 0){
                for( List<DataRecord> ancestors : genModList2){
                    if( ancestors != null && ancestors.size() > 0){
                        print("[WARNING] Sample " + this.IGO_ID + " contains a 'mouse genetic modified' data record! We need to figure out if we need to create a new genome");
                        return;
                    }
                }
            }
        }
       
    }
   
    /** 
     * This is specific to to the RNASeq/ Ambiguous request type. Other request will be using differnet values in this.
     **/
    void grabRequestSpecificValues(Map<String, Object> fieldMap){    //Changes between ReqTypes
        // This is specific to this rnaseq/ambiguous request.
        // Other requests will be using a different key
        this.SAMPLE_CLASS = setFromMap(this.SAMPLE_CLASS, "TumorOrNormal", fieldMap);

    }
     
    /**
     * Returns either value in map m with key LIMS_field or a default value (LS_Default/fieldDefaults value/#EMPTY). LS_Default is the default value to return. If it is null, method checks fieldDefaults hash to see if there is a default value there.<br>Then it gets value from key LIMS_field in map m. 
     * if the value is NOT empty, return the value, if it is empty, return the LS_Default value. Probably should explain this better... 
     **/
    String setFromMap(String LS_default, String LIMS_field, Map<String, Object> m){
        // Grabs values from map, makes sure that the value is not null
        String val0 = LS_default;
        String val;
        if(val0 == null || Objects.equals(String.valueOf(val0), Constants.NULL))  {
            val0 = fieldDefaults.getOrDefault(LIMS_field, Constants.EMPTY);
        }

        try{
            val = m.get(LIMS_field).toString();
        } catch(NullPointerException f){
            return val0;
        }
        if(val.isEmpty() || val == Constants.NULL){
            return val0;
        }
        return val;
    }
    
    /** 
     * Beautiful print function. If message starts with square brakect, assume it is error/warning/log and should be added to log_messages. Then message is printed to stdout <br>
     * If print starts with "[ERROR]", change exitLater to true!
     **/
    void print(String message){
        // Log and print to stdout
        if(message.startsWith("[")){
            if(message.startsWith("[ERROR]")){
                exitLater = true;
            }
            log_messages.add(message);
        }
        System.out.println(message);
    }

    public static HashMap<String, String> getSampleRenames(){
        return sampleRenames;
    }
    
    public static Boolean isXenograftProject(){
    	return xenograftProject;
    }
    
    public static ArrayList<String> getLogMessages(){
        return new ArrayList<String>(log_messages);
    }

    public static Boolean exitLater(){
        return exitLater;
    }

    /** 
     * Adds and returns all declared fields in hashmap (does this work??)
     **/
    public LinkedHashMap<String, String> SendInfoToMap(){
        LinkedHashMap<String, String> myMap = new LinkedHashMap<>();
        for (String field : this.valid_fields){
            String val = "";
            try{
                Field f = this.getClass().getDeclaredField(field);
                val = String.valueOf(f.get(this));
            } catch (Throwable a) {
                logger.logError(a);
                a.printStackTrace();
            }
            myMap.put(field, val);
        }
        return myMap;
    } 
}
