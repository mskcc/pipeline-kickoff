package org.mskcc.kickoff.config;

import org.apache.log4j.*;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;

public class LogConfigurer {
    public void configurePmLog() {
        FileAppender fileAppender = new FileAppender();
        fileAppender.setName(Constants.PM_LOGGER);
        fileAppender.setFile(Utils.getFullOutputProjectPath() + "/logs/" + Utils.getPmLogFileName());
        fileAppender.setLayout(new PatternLayout("[%p] %m%n"));
        fileAppender.setThreshold(Level.TRACE);
        fileAppender.setAppend(false);
        fileAppender.activateOptions();

        Logger.getLogger(Constants.PM_LOGGER).addAppender(fileAppender);
    }

    public void configureDevLog() {
        DailyRollingFileAppender rollingFileAppender = new DailyRollingFileAppender();
        rollingFileAppender.setName(Constants.DEV_LOGGER);
        rollingFileAppender.setFile(Utils.getDevLogFileName());
        rollingFileAppender.setDatePattern("'.'dd-MM-yy");
        rollingFileAppender.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %C:%L - %m%n"));
        rollingFileAppender.setThreshold(Level.INFO);
        rollingFileAppender.setAppend(true);
        rollingFileAppender.activateOptions();

        Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);
        devLogger.addAppender(rollingFileAppender);

        ConsoleAppender consoleAppender = new ConsoleAppender();
        consoleAppender.setTarget("System.out");
        consoleAppender.setLayout(new EnhancedPatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %C:%L - %m%n"));
        consoleAppender.setThreshold(Level.INFO);
        consoleAppender.activateOptions();

        devLogger.addAppender(consoleAppender);
    }
}
