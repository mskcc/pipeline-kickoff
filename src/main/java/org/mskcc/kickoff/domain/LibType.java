package org.mskcc.kickoff.domain;

import java.util.HashMap;
import java.util.Map;

public enum LibType {
    TRU_SEQ_POLY_A_SELECTION_NON_STRANDED("TruSeq Poly-A Selection Non-Stranded"),
    TRU_SEQ_POLY_A_SELECTION_STRANDED("TruSeq Poly-A Selection Stranded"),
    TRU_SEQ_SM_RNA("TruSeq smRNA"),
    TRU_SEQ_RIBO_DEPLETE("TruSeq RiboDeplete"),
    TRU_SEQ_FUSION_DISCOVERY("TruSeq Fusion Discovery"),
    SMARTER_AMPLIFICATION("SMARTer Amplification"),
    KAPA_M_RNA_STRANDED("KAPA mRNA Stranded");

    private String value;

    private static final Map<String, LibType> valueToLibType = new HashMap<>();

    static {
        for (LibType libType : LibType.values()) {
            valueToLibType.put(libType.value.toLowerCase(), libType);
        }
    }

    LibType(String value) {
        this.value = value;
    }


    @Override
    public String toString() {
        return value;
    }
}
