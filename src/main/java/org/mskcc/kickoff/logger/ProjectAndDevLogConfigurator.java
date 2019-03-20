package org.mskcc.kickoff.logger;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.bic.util.Constants;
import org.mskcc.kickoff.bic.util.Utils;

public class ProjectAndDevLogConfigurator implements LogConfigurator {
    @Override
    public void configureProjectLog(String projectFilePath) {
        Logger pmLogger = Logger.getLogger(Constants.PM_LOGGER);
        FileAppender appender = (FileAppender) pmLogger.getAppender(Constants.PM_LOGGER);
        appender.setFile(String.format("%s/logs/%s", projectFilePath, Utils.getPmLogFileName()));
        appender.activateOptions();
    }

    @Deprecated
    @Override
    public void configureDevLog(String pipeline) {
        Logger devLogger = Logger.getLogger(org.mskcc.kickoff.bic.util.Constants.DEV_LOGGER);

        FileAppender appender = (FileAppender) devLogger.getAppender(org.mskcc.kickoff.bic.util.Constants.DEV_LOGGER);
        appender.setFile(Utils.getDevLogFileName(pipeline));
        appender.activateOptions();
    }
}
