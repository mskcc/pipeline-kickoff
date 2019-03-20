package org.mskcc.kickoff.bic.domain.sample;

import java.util.HashMap;
import java.util.Map;

public enum TumorNormalType {
    TUMOR("Tumor"),
    NORMAL("Normal");

    private static final Map<String, TumorNormalType> valueToTumorNormalType = new HashMap<>();

    static {
        for (TumorNormalType tumorNormalType : values()) {
            valueToTumorNormalType.put(tumorNormalType.value, tumorNormalType);
        }
    }
    private String value;

    TumorNormalType(String value) {
        this.value = value;
    }

    public static TumorNormalType getByValue(String value) {
        if(!valueToTumorNormalType.containsKey(value))
            throw new RuntimeException(String.format("Unsupported Tumor/Normal type: %s", value));
        return valueToTumorNormalType.get(value);
    }
}
