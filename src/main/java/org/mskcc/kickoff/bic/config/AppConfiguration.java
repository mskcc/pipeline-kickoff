package org.mskcc.kickoff.bic.config;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.mskcc.kickoff.bic.domain.PassedRunPredicate;
import org.mskcc.kickoff.bic.archive.FilesArchiver;
import org.mskcc.kickoff.bic.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.bic.archive.RunPipelineLogger;
import org.mskcc.kickoff.bic.generator.*;
import org.mskcc.kickoff.bic.lims.QueryImpactProjectInfo;
import org.mskcc.kickoff.bic.printer.MappingFilePrinter;
import org.mskcc.kickoff.bic.printer.OutputFilesPrinter;
import org.mskcc.kickoff.bic.printer.SampleKeyPrinter;
import org.mskcc.kickoff.bic.proxy.RequestProxy;
import org.mskcc.kickoff.bic.util.BasicMail;
import org.mskcc.kickoff.bic.validator.LimsProjectNameValidator;
import org.mskcc.kickoff.bic.validator.ProjectNamePredicate;
import org.mskcc.kickoff.bic.validator.ProjectNameValidator;
import org.mskcc.kickoff.bic.validator.RequestValidator;
import org.mskcc.kickoff.bic.velox.VeloxRequestProxy;
import org.mskcc.kickoff.config.DevConfiguration;
import org.mskcc.kickoff.config.ProdConfiguration;
import org.mskcc.kickoff.config.TestConfiguration;
import org.mskcc.kickoff.logger.LogConfigurator;
import org.mskcc.kickoff.logger.ProjectAndDevLogConfigurator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

@Configuration
@Import({ProdConfiguration.class, DevConfiguration.class, TestConfiguration.class})
public class AppConfiguration {

    public static void configureLogger(String loggerPropertiesName) {
        LogManager.resetConfiguration();

        if (new File(loggerPropertiesName).exists())
            PropertyConfigurator.configure(new FileSystemResource(loggerPropertiesName).getFile().getAbsoluteFile()
                    .toString());
        else {
            try {
                PropertyConfigurator.configure(new ClassPathResource(loggerPropertiesName).getURL());
            } catch (IOException e) {
                PropertyConfigurator.configure(Loader.getResource(loggerPropertiesName));
            }
        }

        Logger.getRootLogger().setLevel(Level.OFF);
    }

    @Bean
    public ProjectNameValidator projectNameValidator() {
        return new LimsProjectNameValidator(projectNamePredicate());
    }

    @Bean
    public Predicate<String> projectNamePredicate() {
        return new ProjectNamePredicate();
    }

    @Bean
    public LogConfigurator logConfigurer() {
        LogConfigurator logConfigurator = new ProjectAndDevLogConfigurator();
        return logConfigurator;
    }

    @Bean
    public ManifestGenerator manifestGenerator() {
        return new FilesGenerator();
    }

    @Bean
    public RequestProxy requestProxy() {
        return new VeloxRequestProxy(projectFilesArchiver());
    }

    @Bean
    public MappingFilePrinter mappingFilePrinter() {
        return new MappingFilePrinter(basicMail());
    }

    @Bean
    public SampleKeyPrinter sampleKeyFileGenerator() {
        return new SampleKeyPrinter();
    }

    @Bean
    public ProjectFilesArchiver projectFilesArchiver() {
        return new ProjectFilesArchiver();
    }

    @Bean
    public RunPipelineLogger runPipelineLogger() {
        return new RunPipelineLogger();
    }

    @Bean
    public PassedRunPredicate passedRunPredicate() {
        return new PassedRunPredicate();
    }

    @Bean
    public QueryImpactProjectInfo queryImpactProjectInfo() {
        return new QueryImpactProjectInfo();
    }

    @Bean
    public OutputFilesPrinter manifestFilesPrinter() {
        return new OutputFilesPrinter(pairingsResolver(), mappingFilePrinter(), sampleKeyFileGenerator());
    }

    @Bean
    public FilesArchiver filesArchiver() {
        return new FilesArchiver();
    }

    @Bean
    public PairingsResolver pairingsResolver() {
        return new PairingsResolver(pairingInfoRetriever(), smartPairingRetriever());
    }

    @Bean
    public PairingInfoRetriever pairingInfoRetriever() {
        return new PairingInfoRetriever();
    }

    @Bean
    public SmartPairingRetriever smartPairingRetriever() {
        return new SmartPairingRetriever();
    }

    @Bean
    public RequestValidator requestValidator() {
        return new RequestValidator();
    }

    @Bean
    public BasicMail basicMail() {
        return new BasicMail();
    }
}
