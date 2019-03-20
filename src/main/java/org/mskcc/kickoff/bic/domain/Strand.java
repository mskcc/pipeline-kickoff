package org.mskcc.kickoff.bic.domain;

public enum Strand {
    REVERSE("Reverse"),
    NONE("None"),
    EMPTY("");

    private String value;

    Strand(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
