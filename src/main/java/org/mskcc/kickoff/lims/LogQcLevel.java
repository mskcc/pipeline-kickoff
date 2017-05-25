package org.mskcc.kickoff.lims;

import org.apache.log4j.Level;

class LogQcLevel extends Level {
    public LogQcLevel(int level, String levelStr, int syslogEquivalent) {
        super(level, levelStr, syslogEquivalent);
    }
}
