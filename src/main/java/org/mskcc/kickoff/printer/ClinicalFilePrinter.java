package org.mskcc.kickoff.printer;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.util.Constants;

import java.util.function.Predicate;

public class ClinicalFilePrinter extends ClinicalPatientFilePrinter {
    private final String manualClinicalHeader = "SAMPLE_ID:CORRECTED_CMO_ID,PATIENT_ID:CMO_PATIENT_ID,COLLAB_ID:INVESTIGATOR_SAMPLE_ID,SAMPLE_TYPE:SAMPLE_TYPE,GENE_PANEL:BAIT_VERSION,ONCOTREE_CODE:ONCOTREE_CODE,SAMPLE_CLASS:SAMPLE_CLASS,SPECIMEN_PRESERVATION_TYPE:SPECIMEN_PRESERVATION_TYPE,SEX:SEX,TISSUE_SITE:TISSUE_SITE";

    @Override
    protected String getManualHeader() {
        return manualClinicalHeader;
    }

    @Override
    protected String getFileType() {
        return Constants.DATA_CLINICAL;
    }

    @Override
    protected String getOutputFilenameEnding() {
        return "_sample_data_clinical.txt";
    }

    @Override
    protected Predicate<Sample> getSamplePredicate() {
        return sample -> sample.isTumor();
    }
}
