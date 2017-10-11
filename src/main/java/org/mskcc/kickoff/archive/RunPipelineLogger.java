package org.mskcc.kickoff.archive;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.mskcc.kickoff.config.Arguments.rerunReason;

@Component
class RunPipelineLogger {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Value("${archivePath}")
    private String archivePath;

    void invoke(KickoffRequest request) {
        boolean newFile = false;
        // If this project already has a pipeline run log add rerun information to it.
        // IF the rerun number has already been marked in there, just add to it....
        // Create file is not created before:
        File archiveProject = new File(archivePath + "/" + Utils.getFullProjectNameWithPrefix(request.getId()));

        if (!archiveProject.exists()) {
            DEV_LOGGER.info(String.format("Creating archive directory: %s", archiveProject));
            archiveProject.mkdirs();
        }

        File runLogFile = new File(archiveProject + "/" + Utils.getFullProjectNameWithPrefix(request.getId()) + "_runs.log");
        if (!runLogFile.exists()) {
            newFile = true;
        }

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
        Date now = new Date();
        String reason = Constants.NA;
        if (rerunReason != null && !rerunReason.isEmpty()) {
            reason = rerunReason;
        }

        try {
            PrintWriter pw = new PrintWriter(new FileWriter(runLogFile, true), false);

            if (newFile) {
                pw.write("Date\tTime\tRun_Number\tReason_For_Rerun\n");
            }

            pw.println(dateFormat.format(now) + "\t" + timeFormat.format(now) + "\t" + request.getRunNumber() + "\t" + reason);

            pw.close();
        } catch (Exception e) {
            DEV_LOGGER.warn(String.format("Exception thrown while creating run log file: %s", runLogFile), e);
        }
    }
}
