package org.mskcc.kickoff.characterisationTest;

public enum OutputType {
    ACTUAL("actual"),
    EXPECTED("expected"),
    ARCHIVE("archive");

    private final String typeName;

    OutputType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}
