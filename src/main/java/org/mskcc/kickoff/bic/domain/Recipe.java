package org.mskcc.kickoff.bic.domain;

import java.util.HashMap;
import java.util.Map;

public enum Recipe {
    AMPLI_SEQ("AmpliSeq"),
    AMPLICON_SEQ("AmpliconSeq"),
    ARCHER_FUSION_PLEX("ArcherFusionPlex"),
    ATAC_SEQ("ATACSeq"),
    CH_IP_SEQ("ChIPSeq"),
    CRISPR_SCREEN("CRISPRScreen"),
    CRISPR_SEQ("CRISPRSeq"),
    CUSTOM_CAPTURE("CustomCapture"),
    DD_PCR("ddPCR"),
    DROP_SEQ("DropSeq"),
    EXTRACTION_USER_PICKUP("Extraction-UserPickup"),
    FINGERPRINTING("Fingerprinting"),
    FUSION_DISCOVERY_SEQ("FusionDiscoverySeq"),
    HEME_PACT_V_3("HemePACT_v3"),
    HEME_PACT_V_4("HemePACT_v4"),
    IDT_Exome_v1("IDT_Exome_v1"),
    IMMUNO_SEQ("ImmunoSeq"),
    IMPACT_410("IMPACT410"),
    IMPACT_410_PLUS("IMPACT410+"),
    IMPACT_468("IMPACT468"),
    IN_DROP_SEQ("InDropSeq"),
    IWG("IWG"),
    M_IMPACT_V_1("M-IMPACT_v1"),
    METHYL_MINER("MethylMiner"),
    MULTIPLE_OF_THE_ABOVE("(Multiple of the Above)"),
    NANO_STRING("nanoString"),
    QC_DISCARD("QC_Discard"),
    QC_PICKUP("QC_Pickup"),
    R_LOOP_DNA_SEQ("R_Loop_DNA_Seq"),
    RIBO_PROFILE_SEQ("RiboProfileSeq"),
    RNA_SEQ("RNASeq"),
    RNA_SEQ_POLY_A("RNASeq_PolyA"),
    RNA_SEQ_RIBO_DEPLETE("RNASeq_RiboDeplete"),
    RRBS("RRBS"),
    SH_RNA_SEQ("shRNASeq"),
    SHALLOW_WGS("ShallowWGS"),
    SMARTER_AMP_SEQ("SMARTerAmpSeq"),
    SPO_11_OLIGO("Spo11Oligo"),
    WHOLE_EXOME_SEQUENCING("WholeExomeSequencing"),
    WHOLE_GENOME_BISULFATE_SEQUENCING("WholeGenomeBisulfateSequencing"),
    WHOLE_GENOME_SEQUENCING("WholeGenomeSequencing");

    private static final Map<String, Recipe> valueToRecipe = new HashMap<>();

    static {
        for (Recipe recipe : Recipe.values()) {
            valueToRecipe.put(recipe.value.toLowerCase(), recipe);
        }
    }

    public String getValue() {
        return value;
    }

    private String value;

    Recipe(String value) {
        this.value = value;
    }

    public static Recipe getRecipeByValue(String value) {
        if(value == null || value.isEmpty())
            throw new UnsupportedRecipeException("Empty Recipe");
        String loweredValue = value.toLowerCase();
        if (!valueToRecipe.containsKey(loweredValue)) {
            throw new UnsupportedRecipeException(String.format("Unsupported recipe: %s", value));
        }
        return valueToRecipe.get(loweredValue);
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static class UnsupportedRecipeException extends RuntimeException {
        public UnsupportedRecipeException(String message) {
            super(message);
        }
    }
}

