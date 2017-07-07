package org.mskcc.kickoff.archive;

import org.mskcc.kickoff.domain.Request;
import org.springframework.beans.factory.annotation.Autowired;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.mskcc.kickoff.config.Arguments.krista;
import static org.mskcc.kickoff.config.Arguments.shiny;

public class FilesArchiver {
    @Autowired
    private ProjectFilesArchiver projectFilesArchiver;

    @Autowired
    private RunPipelineLogger runPipelineLogger;

    public void archive(Request request) {
        // if this is not a shiny run, and the project stuff is valid, copy to archive and add/append log files.
        if (!shiny && !krista) {
            DateFormat archiveDateFormat = new SimpleDateFormat("yyyyMMdd");
            String date = archiveDateFormat.format(new Date());
            copyToArchive(request, date);
            printToPipelineRunLog(request);
        }
    }

    private void printToPipelineRunLog(Request request) {
        runPipelineLogger.invoke(request);
    }

    private void copyToArchive(Request request, String dateDir) {
        copyToArchive(request, dateDir, "");
    }

    private void copyToArchive(Request request, String dateDir, String suffix) {
        projectFilesArchiver.invoke(request, dateDir, suffix);
    }
}
