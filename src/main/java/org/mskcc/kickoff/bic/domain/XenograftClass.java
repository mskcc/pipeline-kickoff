package org.mskcc.kickoff.bic.domain;

import java.util.HashMap;
import java.util.Map;

public enum XenograftClass {
    PDX("PDX"),
    XENOGRAFT("Xenograft"),
    XENOGRAFT_DERIVED_CELL_LINE("XenograftDerivedCellLine");

    private String value;

    XenograftClass(String value) {
        this.value = value;
    }

    private static Map<String, XenograftClass> valueToXenograftClass = new HashMap<>();

    static {
        for (XenograftClass xenograftClass : values()) {
            valueToXenograftClass.put(xenograftClass.value, xenograftClass);
        }

    }

    public static boolean containsValue(String value) {
        return valueToXenograftClass.containsKey(value);
    }

    public String getValue() {
        return value;
    }
}
