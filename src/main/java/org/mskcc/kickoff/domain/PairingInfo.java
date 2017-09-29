package org.mskcc.kickoff.domain;

public class PairingInfo {
    private final String tumorIgoId;
    private final String normalIgoId;

    public PairingInfo(String tumorIgoId, String normalIgoId) {
        this.tumorIgoId = tumorIgoId;
        this.normalIgoId = normalIgoId;
    }

    public String getTumorIgoId() {
        return tumorIgoId;
    }

    public String getNormalIgoId() {
        return normalIgoId;
    }
}
