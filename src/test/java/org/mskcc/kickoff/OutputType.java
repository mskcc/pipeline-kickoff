package org.mskcc.kickoff;

public enum OutputType {
    ACTUAL("actual"),
    EXPECTED("expected"),
    ARCHIVE("archive");

    private String typeName;

    OutputType(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }
}
