package org.mskcc.kickoff.config;

import org.apache.log4j.*;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

public class ProjectAndDevLogConfigurator implements LogConfigurator {
    @Override
    public void configureProjectLog(String projectFilePath) {
        Logger devLogger = Logger.getLogger(Constants.PM_LOGGER);
        FileAppender appender = (FileAppender) devLogger.getAppender(Constants.PM_LOGGER);
        appender.setFile(String.format("%s/logs/%s", projectFilePath, Utils.getPmLogFileName()));
        appender.activateOptions();
    }

    @Override
    public void configureDevLog() {
        Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);
        DailyRollingFileAppender appender = (DailyRollingFileAppender) devLogger.getAppender(Constants.DEV_LOGGER);
        appender.setFile(Utils.getDevLogFileName());
        appender.activateOptions();
    }
}
