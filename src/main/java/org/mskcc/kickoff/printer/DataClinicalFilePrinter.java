package org.mskcc.kickoff.printer;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class DataClinicalFilePrinter extends ClinicalPatientFilePrinter {
    public static final String FILE_NAME = "_sample_data_clinical.txt";
    private final Map<String, String> headerToFieldName = new LinkedHashMap<>();

    {
        headerToFieldName.put("SAMPLE_ID", "CORRECTED_CMO_ID");
        headerToFieldName.put("PATIENT_ID", "CMO_PATIENT_ID");
        headerToFieldName.put("COLLAB_ID", "INVESTIGATOR_SAMPLE_ID");
        headerToFieldName.put("SAMPLE_TYPE", "SAMPLE_TYPE");
        headerToFieldName.put("GENE_PANEL", "BAIT_VERSION");
        headerToFieldName.put("ONCOTREE_CODE", "ONCOTREE_CODE");
        headerToFieldName.put("SAMPLE_CLASS", "SAMPLE_CLASS");
        headerToFieldName.put("SPECIMEN_PRESERVATION_TYPE", "SPECIMEN_PRESERVATION_TYPE");
        headerToFieldName.put("SEX", "SEX");
        headerToFieldName.put("TISSUE_SITE", "TISSUE_SITE");
    }

    @Autowired
    public DataClinicalFilePrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    @Override
    protected Map<String, String> getManualHeader() {
        return headerToFieldName;
    }

    @Override
    protected String getOutputFilenameEnding() {
        return FILE_NAME;
    }

    @Override
    protected Predicate<Sample> getSamplePredicate() {
        return s -> s.isTumor();
    }

    @Override
    protected String getFileType() {
        return Constants.DATA_CLINICAL;
    }

    @Override
    protected void notifyObservers() {
        observerManager.notifyObserversOfFileCreated(ManifestFile.CLINICAL);
    }
}