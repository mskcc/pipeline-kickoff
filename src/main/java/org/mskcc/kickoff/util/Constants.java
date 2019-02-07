package org.mskcc.kickoff.util;

public class Constants {
    public static final int MINIMUM_NUMBER_OF_READS = 1000000;
    public static final String ASSAY = "Assay";
    public static final String BAIT_SET = "BaitSet";
    public static final String BAIT_SET_TO_DESIGN_FILE_MAPPING = "BaitSetToDesignFileMapping";
    public static final String BAIT_VERSION = "BAIT_VERSION";
    public static final String BARCODE_ID = "BARCODE_ID";
    public static final String BARCODE_INDEX = "BARCODE_INDEX";
    public static final String CAPTURE_BAIT_SET = "CAPTURE_BAIT_SET";
    public static final String CAPTURE_INPUT = "CAPTURE_INPUT";
    public static final String CMO_PATIENT_ID = "CMO_PATIENT_ID";
    public static final String CMO_SAMPLE_ID = "CMO_SAMPLE_ID";
    public static final String CORRECT = "Correct";
    public static final String CORRECTED_CMO_ID = "CORRECTED_CMO_ID";
    public static final String DATA_CLINICAL = "data_clinical";
    public static final String DEFAULT_RERUN_REASON = "User Request";
    public static final String DESIGN_FILE_NAME = "DesignFile";
    public static final String DEV_LOGGER = "devLogger";
    public static final String DEV_PROFILE = "dev";
    public static final String EMPTY = "#EMPTY";
    public static final String ERROR = "ERROR";
    public static final String EXAMPLE = "Example";
    public static final String EXCEL_ROW_TYPE_HEADER = "header";
    public static final String EXCLUDE_RUN_ID = "EXCLUDE_RUN_ID";
    public static final String EXOME = "Exome";
    public static final String FAILED = "Failed";
    public static final String FAILED_COMPLETED = "Failed - Completed";
    public static final String FFPEPOOLEDNORMAL = "FFPEPOOLEDNORMAL";
    public static final String FORCED = "#FORCED";
    public static final String FROZENPOOLEDNORMAL = "FROZENPOOLEDNORMAL";
    public static final String HOLD_PROFILE = "hold";
    public static final String HUMAN_ABREV = "b37";
    public static final String IGO_ID = "IGO_ID";
    public static final String IGO_PROFILE = "igo";
    public static final String INCLUDE_RUN_ID = "INCLUDE_RUN_ID";
    public static final String INCORRECT = "Incorrect";
    public static final String INNOVATION_PROJECT_ID = "05500";
    public static final String INVESTIGATOR_PATIENT_ID = "INVESTIGATOR_PATIENT_ID";
    public static final String INVESTIGATOR_SAMPLE_ID = "INVESTIGATOR_SAMPLE_ID";
    public static final String LOG_FILE_PATH = "logs";
    public static final String LOG_FILE_PREFIX = "Log_";
    public static final String MANIFEST_SAMPLE_ID = "MANIFEST_SAMPLE_ID";
    public static final String MANUAL_EXOME_BAIT_VERSION_HUMAN = "AgilentExon_51MB_b37_v3";
    public static final String MANUAL_EXOME_XENOGRAFT_BAIT_VERSION_HUMAN_MOUSE = "AgilentExon_51MB_b37_mm10_v3";
    public static final String MATCHED_NORMAL = "MatchedNormal";
    public static final String MIXED = "mixed";
    public static final String MOUSE_ABREV = "mm10";
    public static final String MOUSEPOOLEDNORMAL = "MOUSEPOOLEDNORMAL";
    public static final String NA = "NA";
    public static final String NA_LOWER_CASE = "na";
    public static final String NO_PM = "NO PM";
    public static final String NoKAPACaptureProtocol1 = "#NoKAPACaptureProtocol1";
    public static final String NoKAPACaptureProtocol2 = "#NoKAPACaptureProtocol2";
    public static final String NORMAL = "Normal";
    public static final String NORMAL_POOL = "NormalPool";
    public static final String NULL = "null";
    public static final String ONCOTREE_CODE = "ONCOTREE_CODE";
    public static final String OVERRIDE_BAIT_SET = "OVERRIDE_BAIT_SET";
    public static final String PAIRING_INFO = "PairingInfo";
    public static final String PASSED = "Passed";
    public static final String PATIENT = "patient";
    public static final String PM_LOGGER = "pmLogger";
    public static final String POOLNORMAL = "POOLNORMAL";
    public static final String PROD_PROFILE = "prod";
    public static final String PROJECT_ID = "ProjectID";
    public static final String PROJECT_PREFIX = "Proj_";
    public static final String READ_ME = "ReadMe";
    public static final String RECIPE = "recipe";
    public static final String REQ_ID = "REQ_ID";
    public static final String REQUEST_05500 = "05500";
    public static final String REQUEST_NAME = "RequestName";
    public static final String REQUIRED_ADDITIONAL_READS = "Required-Additional-Reads";
    public static final String RESERVED_TEST_PHRASE = "banannaGram72";
    public static final String RUN_INFO_PATH = "argumentsInfo.txt";
    public static final String SAMPLE_CLASS = "SAMPLE_CLASS";
    public static final String SAMPLE_KEY = "SampleKey";
    public static final String SAMPLE_RENAME = "SampleRename";
    public static final String SAMPLE_SET_PREFIX = "set_";
    public static final String SAMPLE_TYPE = "SAMPLE_TYPE";
    public static final String SEQ_IGO_ID = "SEQ_IGO_ID";
    public static final String SEX = "SEX";
    public static final String SPECIES = "SPECIES";
    public static final String SPECIMEN_PRESERVATION_TYPE = "SPECIMEN_PRESERVATION_TYPE";
    public static final String SPIKE_IN_GENES = "SPIKE_IN_GENES";
    public static final String STATUS = "STATUS";
    public static final String STRANDED = "Stranded";
    public static final String TANGO_PROFILE = "tango";
    public static final String TEST_PROFILE = "test";
    public static final String TISSUE_SITE = "TISSUE_SITE";
    public static final String TUMOR = "Tumor";
    public static final String UNDEFINED = "undefined";
    public static final String UNDER_REVIEW = "Under-Review";
    public static final String UNKNOWN = "Unknown";
    public static final String PAIRED_END = "PE";

    public static class Manifest {
        public static final String SAMPLE_INFO = "SampleInfo";
        public static final String SAMPLE_RENAMES = "SampleRenames";
        public static final String OLD_NAME = "OldName";
        public static final String NEW_NAME = "NewName";
    }

    public static class Excel {

        public static final String INSTRUCTIONS = "instructions";
        public static final String EMPTY = "#empty";
        public static final String SPECIMEN_COLLECTION_YEAR = "SPECIMEN_COLLECTION_YEAR";
    }

    public static class ProjectInfo {
        public static final String ALTERNATE_EMAILS = "Alternate_E-mails";
        public static final String ASSAY = "Assay";
        public static final String BIOINFORMATIC_REQUEST = "Bioinformatic_Request";
        public static final String CMO_PROJECT_BRIEF = "CMO_Project_Brief";
        public static final String CMO_PROJECT_ID = "CMO_Project_ID";
        public static final String DATA_ANALYST = "Data_Analyst";
        public static final String DATA_ANALYST_EMAIL = "Data_Analyst_E-mail";
        public static final String DESIGN_FILE = "DesignFile";
        public static final String FINAL_PROJECT_TITLE = "Final_Project_Title";
        public static final String IGO_PROJECT_ID = "IGO_Project_ID";
        public static final String LAB_HEAD = "Lab_Head";
        public static final String LAB_HEAD_E_MAIL = "Lab_Head_E-mail";
        public static final String NUMBER_OF_SAMPLES = "NumberOfSamples";
        public static final String PLATFORM = "Platform";
        public static final String PROJECT_MANAGER = "Project_Manager";
        public static final String PROJECT_MANAGER_EMAIL = "Project_Manager_Email";
        public static final String README_INFO = "Readme_Info";
        public static final String REQUESTOR = "Requestor";
        public static final String REQUESTOR_E_MAIL = "Requestor_E-mail";
        public static final String SAMPLE_TYPE = "Sample_Type";
        public static final String SPECIES = "Species";
        public static final String SPIKEIN_DESIGN_FILE = "SpikeinDesignFile";
        public static final String TUMOR_TYPE = "TumorType";
        public static final String PI_EMAIL = "PIemail";
        public static final String PI_FIRSTNAME = "PIFirstName";
        public static final String PI_LASTNAME = "PILastName";
        public static final String CONTACT_NAME = "ContactName";

    }
}
