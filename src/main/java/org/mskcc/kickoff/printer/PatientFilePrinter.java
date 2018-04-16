package org.mskcc.kickoff.printer;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class PatientFilePrinter extends ClinicalPatientFilePrinter {
    private final Map<String, String> manualMappingPatientHeader = new HashMap<>();

    {
        manualMappingPatientHeader.put("Pool", "REQ_ID");
        manualMappingPatientHeader.put("Sample_ID", "CORRECTED_CMO_ID");
        manualMappingPatientHeader.put("Collab_ID", "INVESTIGATOR_SAMPLE_ID");
        manualMappingPatientHeader.put("Patient_ID", "CMO_PATIENT_ID");
        manualMappingPatientHeader.put("Class", "SAMPLE_CLASS");
        manualMappingPatientHeader.put("Sample_type", "SPECIMEN_PRESERVATION_TYPE");
        manualMappingPatientHeader.put("Input_ng", "LIBRARY_INPUT");
        manualMappingPatientHeader.put("Library_yield", "LIBRARY_YIELD");
        manualMappingPatientHeader.put("Pool_input", "CAPTURE_INPUT");
        manualMappingPatientHeader.put("Bait_version", "BAIT_VERSION,Sex:SEX");
    }

    @Autowired
    public PatientFilePrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    @Override
    protected void notifyObservers() {
        observerManager.notifyObserversOfFileCreated(ManifestFile.PATIENT);
    }

    @Override
    protected Map<String, String> getManualHeader() {
        return manualMappingPatientHeader;
    }

    @Override
    protected String getFileType() {
        return Constants.PATIENT;
    }

    @Override
    protected String getOutputFilenameEnding() {
        return "_sample_patient.txt";
    }

    @Override
    protected Predicate<Sample> getSamplePredicate() {
        return sample -> true;
    }
}
