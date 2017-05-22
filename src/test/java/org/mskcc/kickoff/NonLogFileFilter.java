package org.mskcc.kickoff;

import org.mskcc.kickoff.util.Constants;

import java.io.File;
import java.io.FilenameFilter;

class NonLogFileFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return !Constants.LOG_FILE_PATH.equals(name);
    }
}
