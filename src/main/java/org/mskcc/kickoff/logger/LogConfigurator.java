package org.mskcc.kickoff.logger;

public interface LogConfigurator {
    void configureProjectLog(String projectFilePath);

    @Deprecated
    void configureDevLog(String pipeline);
}
