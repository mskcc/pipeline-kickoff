package org.mskcc.kickoff.lims;

import org.apache.log4j.Level;
import org.apache.log4j.Priority;

public class PmLogPriority extends Priority {
    public static final Level SAMPLE_INFO = new LogQcLevel(Level.INFO_INT, "SAMPLE_QC_INFO", 6);
    public static final Level SAMPLE_ERROR = new LogQcLevel(Level.ERROR_INT, "SAMPLE_QC_ERROR", 3);
    public static final Level POOL_INFO = new LogQcLevel(Level.INFO_INT, "POOL_QC_INFO", 6);
    public static final Level POOL_ERROR = new LogQcLevel(Level.ERROR_INT, "POOL_QC_ERROR", 3);
    public static final Level WARNING = new LogQcLevel(Level.WARN_INT, "WARNING", 4);
}
