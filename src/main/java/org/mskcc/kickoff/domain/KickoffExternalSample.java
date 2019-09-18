package org.mskcc.kickoff.domain;

import org.mskcc.domain.Recipe;
import org.mskcc.domain.Run;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.domain.sample.TumorNormalType;
import org.mskcc.util.Constants;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KickoffExternalSample extends Sample {
    private static final org.apache.log4j.Logger DEV_LOGGER = org.apache.log4j.Logger.getLogger(Constants.DEV_LOGGER);

    private String externalId;
    private String filePath;
    private String externalPatientId;
    private String tumorNormal;
    private int counter;
    private String runId;
    private String sampleOrigin;
    private String sampleClass;
    private String cmoId;
    private String nucleidAcid;
    private String patientCmoId;
    private String specimenType;
    private String sex;
    private String oncotreeCode;
    private String baitVersion;
    private String tissueSite;
    private String preservationType;

    public KickoffExternalSample(int counter,
                                 String externalId,
                                 String externalPatientId,
                                 String filePath,
                                 String runId,
                                 String sampleClass,
                                 String sampleOrigin,
                                 String tumorNormal) {
        super(externalId);
        this.counter = counter;
        this.externalId = externalId;
        this.externalPatientId = externalPatientId;
        this.filePath = filePath;
        this.sampleOrigin = sampleOrigin;
        this.runId = runId;
        putRunIfAbsent(runId);
        this.sampleClass = sampleClass;
        this.tumorNormal = tumorNormal;
        put(Constants.INVESTIGATOR_SAMPLE_ID, externalId);
        put(Constants.INVESTIGATOR_PATIENT_ID, externalPatientId);
        put(Constants.SAMPLE_CLASS, sampleClass);
    }

    protected KickoffExternalSample() {
        super("");
    }

    public static KickoffExternalSample convert(ExternalSample externalSample) {
        KickoffExternalSample kickoffExternalSample = new KickoffExternalSample(externalSample.getCounter(),
                externalSample.getExternalId(), externalSample.getExternalPatientId(), externalSample.getFilePath(),
                externalSample.getRunId(), externalSample.getSampleClass(), externalSample.getSampleOrigin(),
                externalSample.getTumorNormal());

        kickoffExternalSample.setBaitVersion(externalSample.getBaitVersion());
        kickoffExternalSample.setCmoId(externalSample.getCmoId());
        kickoffExternalSample.setPatientCmoId(externalSample.getPatientCmoId());
        kickoffExternalSample.setPreservationType(externalSample.getPreservationType());
        kickoffExternalSample.setOncotreeCode(externalSample.getOncotreeCode());
        kickoffExternalSample.setSex(externalSample.getSex());
        kickoffExternalSample.setTissueSite(externalSample.getTissueSite());
        kickoffExternalSample.setCounter(externalSample.getCounter());
        kickoffExternalSample.setNucleidAcid(externalSample.getNucleidAcid());
        kickoffExternalSample.setSpecimenType(externalSample.getSpecimenType());

        DEV_LOGGER.info(String.format("Converted external sample %s to kickoff external sample", externalSample,
                kickoffExternalSample));

        return kickoffExternalSample;
    }

    @Override
    public Recipe getRecipe() {
        return Recipe.getRecipeByValue(baitVersion);
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public Set<Run> getValidRuns() {
        return getRuns().values().stream()
                .collect(Collectors.toSet());
    }

    @Override
    public String getIgoId() {
        return externalId;
    }

    @Override
    public Set<String> getValidRunIds() {
        return getRuns().keySet();
    }

    @Override
    public String getPatientId() {
        return externalPatientId;
    }

    @Override
    public String getCmoPatientId() {
        return patientCmoId;
    }

    @Override
    public String getCmoSampleId() {
        return cmoId;
    }

    @Override
    public String getCorrectedCmoSampleId() {
        return cmoId;
    }

    @Override
    public boolean isTumor() {
        return TumorNormalType.getByValue(tumorNormal) == TumorNormalType.TUMOR;
    }

    public String getExternalRunId() {
        return this.runId;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSampleOrigin() {
        return this.sampleOrigin;
    }

    public void setSampleOrigin(String sampleOrigin) {
        this.sampleOrigin = sampleOrigin;
    }

    public String getSpecimenType() {
        return this.specimenType;
    }

    public void setSpecimenType(String specimenType) {
        this.specimenType = specimenType;
    }

    public String getNucleidAcid() {
        return this.nucleidAcid;
    }

    public void setNucleidAcid(String nucleidAcid) {
        this.nucleidAcid = nucleidAcid;
    }

    public String getCmoId() {
        return this.cmoId;
    }

    public void setCmoId(String cmoId) {
        this.cmoId = cmoId;
        put(Constants.CMO_SAMPLE_ID, cmoId);
        put(Constants.CORRECTED_CMO_ID, cmoId);
    }

    public String getExternalId() {
        return this.externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getPatientCmoId() {
        return this.patientCmoId;
    }

    public void setPatientCmoId(String patientCmoId) {
        this.patientCmoId = patientCmoId;
        put(Constants.CMO_PATIENT_ID, patientCmoId);
    }

    public String getExternalPatientId() {
        return this.externalPatientId;
    }

    public void setExternalPatientId(String externalPatientId) {
        this.externalPatientId = externalPatientId;
    }

    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public String getSampleClass() {
        return sampleClass;
    }

    public void setSampleClass(String sampleClass) {
        this.sampleClass = sampleClass;
    }

    public String getTumorNormal() {
        return tumorNormal;
    }

    public void setTumorNormal(String tumorNormal) {
        this.tumorNormal = tumorNormal;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    @Override
    public boolean hasBarcode() {
        return true;
    }

    @Override
    public List<String> getSeqNames() {
        return Arrays.asList(InstrumentType.DMP_SAMPLE.getValue());
    }

    @Override
    public List<InstrumentType> getInstrumentTypes() {
        return Arrays.asList(InstrumentType.DMP_SAMPLE);
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getOncotreeCode() {
        return oncotreeCode;
    }

    public void setOncotreeCode(String oncotreeCode) {
        this.oncotreeCode = oncotreeCode;
    }

    @Override
    public String getBaitVersion() {
        return baitVersion;
    }

    public void setBaitVersion(String baitVersion) {
        this.baitVersion = baitVersion;
    }

    public String getTissueSite() {
        return tissueSite;
    }

    public void setTissueSite(String tissueSite) {
        this.tissueSite = tissueSite;
    }

    public String getPreservationType() {
        return preservationType;
    }

    public void setPreservationType(String preservationType) {
        this.preservationType = preservationType;
    }

    @Override
    public String toString() {
        return "KickoffExternalSample{" +
                "externalId='" + externalId + '\'' +
                ", filePath='" + filePath + '\'' +
                ", externalPatientId='" + externalPatientId + '\'' +
                ", tumorNormal='" + tumorNormal + '\'' +
                ", counter=" + counter +
                ", runId='" + runId + '\'' +
                ", sampleOrigin='" + sampleOrigin + '\'' +
                ", sampleClass='" + sampleClass + '\'' +
                ", cmoId='" + cmoId + '\'' +
                ", nucleidAcid='" + nucleidAcid + '\'' +
                ", patientCmoId='" + patientCmoId + '\'' +
                ", specimenType='" + specimenType + '\'' +
                ", sex='" + sex + '\'' +
                ", oncotreeCode='" + oncotreeCode + '\'' +
                ", baitVersion='" + baitVersion + '\'' +
                ", tissueSite='" + tissueSite + '\'' +
                ", preservationType='" + preservationType + '\'' +
                '}';
    }
}
