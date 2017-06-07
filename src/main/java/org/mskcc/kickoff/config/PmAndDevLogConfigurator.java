package org.mskcc.kickoff.config;

import org.apache.log4j.*;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

public class PmAndDevLogConfigurator implements LogConfigurator {
    @Override
    public void configurePmLog(String projectFilePath) {
        FileAppender fileAppender = new FileAppender();
        fileAppender.setName(Constants.PM_LOGGER);
        fileAppender.setFile(String.format("%s/logs/%s", projectFilePath, Utils.getPmLogFileName()));
        fileAppender.setLayout(new PatternLayout("[%p] %m%n"));
        fileAppender.setThreshold(Level.ALL);
        fileAppender.setAppend(false);
        fileAppender.activateOptions();

        Logger.getLogger(Constants.PM_LOGGER).addAppender(fileAppender);
    }

    @Override
    public void configureDevLog() {
        Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);
        DailyRollingFileAppender appender = (DailyRollingFileAppender) devLogger.getAppender("debugLogger");
        appender.setFile(Utils.getDevLogFileName());
        appender.activateOptions();
    }
}
