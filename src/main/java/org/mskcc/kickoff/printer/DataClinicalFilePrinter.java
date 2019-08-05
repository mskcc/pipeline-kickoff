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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

@Component
public class DataClinicalFilePrinter extends ClinicalPatientFilePrinter {
    public static final String FILE_NAME = "_sample_data_clinical.txt";
    private final Map<String, String> headerToFieldName = new LinkedHashMap<>();

    {
        headerToFieldName.put("SAMPLE_ID", "CORRECTED_CMO_ID");
        headerToFieldName.put("IGO_ID", "IGO_ID");
        headerToFieldName.put("PATIENT_ID", "CMO_PATIENT_ID");
        headerToFieldName.put("COLLAB_ID", "INVESTIGATOR_SAMPLE_ID");

        // Fixed swap of Sample class and Sample Type according to https://github
        // .com/mskcc/pipeline-kickoff/issues/101 only for data clinical file
        headerToFieldName.put("SAMPLE_TYPE", "SAMPLE_CLASS");
        headerToFieldName.put("SAMPLE_CLASS", "SAMPLE_TYPE");

        headerToFieldName.put("GENE_PANEL", "BAIT_VERSION");
        headerToFieldName.put("ONCOTREE_CODE", "ONCOTREE_CODE");
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

    @Override
    protected void writeExternalTumors(KickoffRequest kickoffRequest, StringBuilder outputText) {
        for (KickoffExternalSample externalSample : kickoffRequest.getTumorExternalSamples()) {
            outputText.append(Utils.sampleNormalization(externalSample.getCmoId()));
            outputText.append("\t");

            outputText.append(Utils.patientNormalization(externalSample.getPatientCmoId()));
            outputText.append("\t");

            outputText.append(externalSample.getExternalId().replace('-', '_'));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSpecimenType()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getBaitVersion()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getOncotreeCode()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSampleClass()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getPreservationType()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getSex()));
            outputText.append("\t");

            outputText.append(getIfAvailable(externalSample.getTissueSite()));
            outputText.append("\n");
        }
    }
}
