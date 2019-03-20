package org.mskcc.kickoff.bic.domain.sample;

public class CmoSampleInfo {
    private String sampleClass;
    private TumorNormalType tumorNormalType;

    public TumorNormalType getTumorNormalType() {
        return tumorNormalType;
    }

    public void setTumorNormalType(TumorNormalType tumorNormalType) {
        this.tumorNormalType = tumorNormalType;
    }

    public String getSampleClass() {
        return sampleClass;
    }

    public void setSampleClass(String sampleClass) {
        this.sampleClass = sampleClass;
    }
}
