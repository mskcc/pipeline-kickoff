package org.mskcc.kickoff.domain;

import java.util.HashMap;
import java.util.Map;

public enum Recipe {
    AMPLICON_SEQ("AmpliconSeq"),
    AMPLI_SEQ("AmpliSeq"),
    ARCHER_FUSION_PLEX("ArcherFusionPlex"),
    ATAC_SEQ("ATACSeq"),
    CH_IP_SEQ("ChIPSeq"),
    CRISPR_SEQ("CRISPRSeq"),
    CRISPR_SCREEN("CRISPRScreen"),
    CUSTOM_CAPTURE("CustomCapture"),
    DD_PCR("ddPCR"),
    DROP_SEQ("DropSeq"),
    EXTRACTION_USER_PICKUP("Extraction-UserPickup"),
    FINGERPRINTING("Fingerprinting"),
    FUSION_DISCOVERY_SEQ("FusionDiscoverySeq"),
    HEME_PACT_V_3("HemePACT_v3"),
    HEME_PACT_V_4("HemePACT_v4"),
    IMPACT_410("IMPACT410"),
    IMPACT_410_PLUS("IMPACT410+"),
    IMPACT_468("IMPACT468"),
    IN_DROP_SEQ("InDropSeq"),
    IMMUNO_SEQ("ImmunoSeq"),
    M_IMPACT_V_1("M-IMPACT_v1"),
    METHYL_MINER("MethylMiner"),
    NANO_STRING("nanoString"),
    QC_DISCARD("QC_Discard"),
    QC_PICKUP("QC_Pickup"),
    R_LOOP_DNA_SEQ("R_Loop_DNA_Seq"),
    RIBO_PROFILE_SEQ("RiboProfileSeq"),
    RRBS("RRBS"),
    RNA_SEQ_POLY_A("RNASeq_PolyA"),
    RNA_SEQ_RIBO_DEPLETE("RNASeq_RiboDeplete"),
    SHALLOW_WGS("ShallowWGS"),
    SH_RNA_SEQ("shRNASeq"),
    SMAR_TER_AMP_SEQ("SMARTerAmpSeq"),
    SPO_11_OLIGO("Spo11Oligo"),
    WHOLE_EXOME_SEQUENCING("WholeExomeSequencing"),
    WHOLE_GENOME_BISULFATE_SEQUENCING("WholeGenomeBisulfateSequencing"),
    WHOLE_GENOME_SEQUENCING("WholeGenomeSequencing"),
    IDT_Exome_v1("IDT_Exome_v1"),
    MULTIPLE_OF_THE_ABOVE("(Multiple of the Above)"),
    IWG("IWG");

    private static final Map<String, Recipe> valueToRecipe = new HashMap<>();

    static {
        for (Recipe recipe : Recipe.values()) {
            valueToRecipe.put(recipe.value.toLowerCase(), recipe);
        }
    }

    private String value;

    Recipe(String value) {
        this.value = value;
    }

    public static Recipe getRecipeByValue(String value) {
        String loweredValue = value.toLowerCase();
        if(!valueToRecipe.containsKey(loweredValue))
            throw new UnsupportedRecipeException(String.format("Recipe: %s doesn't exist", value));
        return valueToRecipe.get(loweredValue);
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

