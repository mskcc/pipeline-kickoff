package org.mskcc.kickoff;

import org.mskcc.kickoff.util.Constants;

import java.io.File;
import java.io.FilenameFilter;

public class RelevantProjectFileFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return !Constants.RUN_INFO_PATH.equals(name);
    }
}
