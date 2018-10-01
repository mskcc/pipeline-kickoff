package org.mskcc.kickoff.notify;

import org.springframework.stereotype.Component;

@Component
public class DoubleSlashNewLineStrategy implements NewLineStrategy {
    @Override
    public String getNewLineSeparator() {
        return "\\n";
    }
}
