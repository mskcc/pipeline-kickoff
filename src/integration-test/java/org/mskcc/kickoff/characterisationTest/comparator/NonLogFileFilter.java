package org.mskcc.kickoff.characterisationTest.comparator;

import org.mskcc.kickoff.util.Constants;

import java.io.File;
import java.io.FilenameFilter;

public class NonLogFileFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return !Constants.LOG_FILE_PATH.equals(name);
    }
}
