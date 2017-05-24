package org.mskcc.kickoff.domain;

import java.util.HashMap;
import java.util.Map;

public enum RequestSpecies {
    HUMAN("Human"),
    MOUSE("Mouse"),
    XENOGRAFT("xenograft"),
    MOUSE_GENETICALLY_MODIFIED("Mouse_GeneticallyModified"),
    BACTERIA("Bacteria"),
    RAT("Rat"),
    CHICKEN("Chicken"),
    D_MELANOGASTER("D.Melanogaster"),
    S_POMBE("S.Pombe"),
    S_CEREVISAE("S.Cerevisae"),
    C_ELEGANS("C.Elegans"),
    ZEBRAFISH("Zebrafish"),
    SYNTHESIZED("Synthesized"),
    OWL("Owl"),
    SQUIRREL("Squirrel"),
    OTHER("other");

    private static final Map<String, RequestSpecies> nameToSpecies = new HashMap<>();

    static {
        for (RequestSpecies species : RequestSpecies.values()) {
            nameToSpecies.put(species.value.toLowerCase(), species);
        }
    }

    private String value;

    RequestSpecies(String value) {
        this.value = value;
    }

    public static RequestSpecies getSpeciesByValue(String value) {
        String loweredValue = value.toLowerCase();
        if(!nameToSpecies.containsKey(loweredValue))
            throw new IllegalArgumentException(String.format("Request species: %s doesn't exist", value));
        return nameToSpecies.get(loweredValue);
    }

    @Override
    public String toString() {
        return value;
    }
}
