package org.mskcc.kickoff.bic.domain;

import org.mskcc.kickoff.bic.util.Constants;

import java.util.HashMap;
import java.util.Map;

public enum QcStatus {
    FAILED(Constants.FAILED),
    FAILED_REPROCESS(Constants.FAILED_REPROCESS),
    PASSED(Constants.PASSED),
    REQUIRED_ADDITIONAL_READS(Constants.REQUIRED_ADDITIONAL_READS),
    UNDER_REVIEW(Constants.UNDER_REVIEW);

    private static final Map<String, QcStatus> valueToQcStatus = new HashMap<>();

    static {
        for (QcStatus qcStatus : values()) {
            valueToQcStatus.put(qcStatus.getValue(), qcStatus);
        }
    }

    private String value;

    QcStatus(String value) {
        this.value = value;
    }

    public static QcStatus getByValue(String value) {
        if (!valueToQcStatus.containsKey(value))
            throw new RuntimeException(String.format("Qc status: %s doesn't exist", value));
        return valueToQcStatus.get(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
