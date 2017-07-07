package org.mskcc.kickoff.logger;

import org.apache.log4j.Level;

class LogQcLevel extends Level {
    LogQcLevel(int level, String levelStr, int syslogEquivalent) {
        super(level, levelStr, syslogEquivalent);
    }
}
