package org.mskcc.kickoff.printer;

public enum ErrorCode {
    AMBIGUOUS_PAIREDNESS,
    FASTQ_DIR_NOT_FOUND,
    NO_SAMPLE_MAPPINGS,
    PAIREDNESS_RETRIEVAL_ERROR,
    REQUEST_INFO_MISSING,
    SEQUENCING_FOLDER_NOT_FOUND,
    UNMATCHED_NORMAL,
    UNMATCHED_TUMOR,
    MAX_NUMBER_SAMPLES_EXCEEDED,
    NOT_AUTORUNNABLE,
    SAMPLES_UNDER_REVIEW,
    SAMPLE_NEEDS_ADDITIONAL_READS,
    RUN_UNDER_REVIEW,
    RUN_NEEDS_ADDITIONAL_READS,
    NO_SAMPLE_QC,
    NO_SEQ_RUNS,
    NO_QC_FOR_RUN,
    NO_VALID_SAMPLES,
    NO_PASSING_SAMPLES,
    NO_SAMPLES,
    DUPLICATE_SAMPLE,
    INCOMPATIBLE_INSTRUMENT_TYPES,
    PAIRING_SEQUENCERS_NOT_COMPATIBLE,
    EXTERNAL_SAMPLE_NOT_FOUND;
}
