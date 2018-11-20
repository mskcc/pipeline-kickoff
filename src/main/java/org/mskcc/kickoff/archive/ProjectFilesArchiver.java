package org.mskcc.kickoff.archive;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.config.FilePermissionConfigurator;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.logger.PmLogPriority;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.nio.file.Files;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class ProjectFilesArchiver {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private final String archivePath;

    public ProjectFilesArchiver(String archivePath) {
        this.archivePath = archivePath;
    }

    public void archive(KickoffRequest request, String dateDir, String suffix) {
        DEV_LOGGER.info(String.format("Archiving files for request: %s", request.getId()));

        File curDir = new File(request.getOutputPath());
        File projDir = new File(String.format("%s/%s/%s", archivePath, Utils.getFullProjectNameWithPrefix(request.getId()), dateDir));

        try {
            if (curDir.exists() && curDir.isDirectory() && Utils.getFilesInDir(curDir).size() > 0) {
                if (!projDir.exists()) {
                    projDir.mkdirs();
                }
                for (File f : Utils.getFilesInDir(curDir)) {
                    File to = new File(String.format("%s/%s%s", projDir, f.getName(), suffix));
                    if (f.isDirectory()) {
                        FileUtils.copyDirectory(f, to);
                        continue;
                    }
                    Files.copy(f.toPath(), to.toPath(), REPLACE_EXISTING);
                    FilePermissionConfigurator.setPermissions(f);
                }
            } else {
                String message = String.format("Cannot copy project files to archive directory: %s, the current directory: %s is not valid or has no files.",
                        projDir, curDir);
                Utils.setExitLater(true);
                PM_LOGGER.log(PmLogPriority.SAMPLE_ERROR, message);
                DEV_LOGGER.log(Level.ERROR, message);
            }

        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while copying files from: %s to archive: %s", curDir, projDir), e);
        }
    }
}
