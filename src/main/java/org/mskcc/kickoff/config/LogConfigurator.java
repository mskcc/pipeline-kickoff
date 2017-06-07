package org.mskcc.kickoff.config;

public interface LogConfigurator {
    void configurePmLog(String projectFilePath);

    void configureDevLog();
}
