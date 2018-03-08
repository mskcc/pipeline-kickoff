package org.mskcc.kickoff.printer;

import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class PatientFilePrinter extends ClinicalPatientFilePrinter {
    private final String manualMappingPatientHeader = "Pool:REQ_ID,Sample_ID:CORRECTED_CMO_ID,Collab_ID:INVESTIGATOR_SAMPLE_ID,Patient_ID:CMO_PATIENT_ID,Class:SAMPLE_CLASS,Sample_type:SPECIMEN_PRESERVATION_TYPE,Input_ng:LIBRARY_INPUT,Library_yield:LIBRARY_YIELD,Pool_input:CAPTURE_INPUT,Bait_version:BAIT_VERSION,Sex:SEX";

    @Autowired
    public PatientFilePrinter(ObserverManager observerManager) {
        super(observerManager);
    }

    @Override
    protected void notifyObservers(KickoffRequest kickoffRequest) {
        observerManager.notifyObserversOfFileCreated(kickoffRequest, ManifestFile.PATIENT);
    }

    @Override
    protected String getManualHeader() {
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
