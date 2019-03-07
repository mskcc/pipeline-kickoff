package org.mskcc.kickoff.printer;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class PatientFilePrinter extends ClinicalPatientFilePrinter {
    private final Map<String, String> manualMappingPatientHeader = new LinkedHashMap<>();

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
        manualMappingPatientHeader.put("Bait_version", "BAIT_VERSION");
        manualMappingPatientHeader.put("Sex", "SEX");
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

    @Override
    protected void writeExternalTumors(KickoffRequest kickoffRequest, StringBuilder outputText) {
        for (KickoffExternalSample externalSample : kickoffRequest.getTumorExternalSamples()) {
            //pool
            outputText.append("NA");
            outputText.append("\t");

            outputText.append(Utils.sampleNormalization(externalSample.getCmoId()));
            outputText.append("\t");

            outputText.append(externalSample.getExternalId().replace('-', '_'));
            outputText.append("\t");

            outputText.append(Utils.patientNormalization(externalSample.getPatientCmoId()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSampleClass()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSpecimenType()));
            outputText.append("\t");

            //input_ng
            outputText.append("NA");
            outputText.append("\t");

            //library_yield
            outputText.append("NA");
            outputText.append("\t");

            //pool_input
            outputText.append("NA");
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getBaitVersion()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSex()));
            outputText.append("\t");
        }
    }
}
