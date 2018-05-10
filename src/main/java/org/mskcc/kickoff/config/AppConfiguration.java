package org.mskcc.kickoff.config;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.mskcc.domain.PassedRunPredicate;
import org.mskcc.kickoff.archive.FilesArchiver;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.archive.RunPipelineLogger;
import org.mskcc.kickoff.generator.*;
import org.mskcc.kickoff.lims.QueryImpactProjectInfo;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.printer.SampleKeyPrinter;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.validator.LimsProjectNameValidator;
import org.mskcc.kickoff.validator.ProjectNamePredicate;
import org.mskcc.kickoff.validator.ProjectNameValidator;
import org.mskcc.kickoff.validator.RequestValidator;
import org.mskcc.kickoff.velox.VeloxRequestProxy;
import org.mskcc.util.BasicMail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.function.Predicate;

@Configuration
@Import({ProdConfiguration.class, DevConfiguration.class, TestConfiguration.class})
public class AppConfiguration {


    static void configureLogger(String loggerPropertiesPath) {
        LogManager.resetConfiguration();
        try {
            PropertyConfigurator.configure(new ClassPathResource(loggerPropertiesPath).getURL());
        } catch (IOException e) {
            PropertyConfigurator.configure(Loader.getResource(loggerPropertiesPath));
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
