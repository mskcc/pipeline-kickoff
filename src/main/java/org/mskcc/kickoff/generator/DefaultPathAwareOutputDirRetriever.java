package org.mskcc.kickoff.generator;

import org.mskcc.kickoff.util.Utils;

import java.io.File;
import java.util.function.Predicate;

public class DefaultPathAwareOutputDirRetriever implements OutputDirRetriever {
    private final String draftProjectFilePath;
    private final Predicate<String> outputDirValidator;

    public DefaultPathAwareOutputDirRetriever(String draftProjectFilePath, Predicate<String> outputDirValidator) {
        this.draftProjectFilePath = draftProjectFilePath;
        this.outputDirValidator = outputDirValidator;
    }

    @Override
    public String retrieve(String projectId, String outputDir) {
        if (outputDirValidator.test(outputDir))
            return String.format("%s/%s", outputDir, Utils.getFullProjectNameWithPrefix(projectId));

        String projectFilePath = String.format("%s/%s", draftProjectFilePath, Utils.getFullProjectNameWithPrefix(projectId));
        new File(projectFilePath).mkdirs();

        return projectFilePath;
    }
}
