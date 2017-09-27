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
import org.mskcc.kickoff.converter.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.converter.SampleSetToRequestConverter;
import org.mskcc.kickoff.generator.*;
import org.mskcc.kickoff.lims.ProjectInfoRetriever;
import org.mskcc.kickoff.printer.MappingFilePrinter;
import org.mskcc.kickoff.printer.OutputFilesPrinter;
import org.mskcc.kickoff.printer.SampleKeyPrinter;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.retriever.RequestDataPropagator;
import org.mskcc.kickoff.validator.*;
import org.mskcc.kickoff.velox.RequestsRetrieverFactory;
import org.mskcc.kickoff.velox.SampleSetProjectPredicate;
import org.mskcc.kickoff.velox.VeloxProjectProxy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.function.Predicate;

@Configuration
@Import({ProdConfiguration.class, DevConfiguration.class, TestConfiguration.class})
public class AppConfiguration {
    @Value("${archivePath}")
    private String archivePath;

    @Value("${designFilePath}")
    private String designFilePath;

    @Value("${resultsPathPrefix}")
    private String resultsPathPrefix;

    @Value("${draftProjectFilePath}")
    private String draftProjectFilePath;

    @Value("${limsConnectionFilePath}")
    private String limsConnectionFilePath;

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
        return new ProjectNamePredicate(sampleSetProjectPredicate(), sampleSetNamePredicate(), singleRequestNamePredicate());
    }

    @Bean
    public SampleSetProjectPredicate sampleSetProjectPredicate() {
        return new SampleSetProjectPredicate();
    }

    @Bean
    public SampleSetNamePredicate sampleSetNamePredicate() {
        return new SampleSetNamePredicate();
    }

    @Bean
    public SingleRequestNamePredicate singleRequestNamePredicate() {
        return new SingleRequestNamePredicate();
    }

    @Bean
    public LogConfigurator logConfigurer() {
        LogConfigurator logConfigurator = new ProjectAndDevLogConfigurator();
        return logConfigurator;
    }

    @Bean
    public ManifestGenerator manifestGenerator() {
        return new FileManifestGenerator();
    }

    @Bean
    public RequestProxy requestProxy() {
        return new VeloxProjectProxy(limsConnectionFilePath, projectFilesArchiver(), requestsRetrieverFactory());
    }

    @Bean
    public MappingFilePrinter mappingFilePrinter() {
        return new MappingFilePrinter();
    }

    @Bean
    public SampleKeyPrinter sampleKeyFileGenerator() {
        return new SampleKeyPrinter();
    }

    @Bean
    public ProjectFilesArchiver projectFilesArchiver() {
        return new ProjectFilesArchiver(archivePath);
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
    public ProjectInfoRetriever projectInfoRetriever() {
        return new ProjectInfoRetriever();
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
    public SampleSetToRequestConverter sampleSetToRequestConverter() {
        return new SampleSetToRequestConverter(projectInfoConverter());
    }

    @Bean
    public SampleSetProjectInfoConverter projectInfoConverter() {
        return new SampleSetProjectInfoConverter();
    }

    @Bean
    public RequestsRetrieverFactory requestsRetrieverFactory() {
        return new RequestsRetrieverFactory(projectInfoRetriever(), requestDataPropagator(), sampleSetToRequestConverter());
    }

    @Bean
    public RequestDataPropagator requestDataPropagator() {
        return new RequestDataPropagator(designFilePath, resultsPathPrefix);
    }

    @Bean
    public OutputDirRetriever outputDirRetriever() {
        return new DefaultPathAwareOutputDirRetriever(draftProjectFilePath, outputDirPredicate());
    }

    @Bean
    public Predicate<String> outputDirPredicate() {
        return new FileExistenceOutputDirValidator();
    }
}
