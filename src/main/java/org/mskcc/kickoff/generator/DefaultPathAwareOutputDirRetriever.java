package org.mskcc.kickoff.generator;

import org.apache.commons.lang3.StringUtils;
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
        String projectFilePath;

        if (!StringUtils.isEmpty(outputDir))
            projectFilePath = overrideDefaultDir(projectId, outputDir);
        else
            projectFilePath = String.format("%s/%s", draftProjectFilePath, Utils.getFullProjectNameWithPrefix(projectId));

        new File(projectFilePath).mkdirs();

        return projectFilePath;
    }

    private String overrideDefaultDir(String projectId, String outputDir) {
        String projectFilePath;
        if (!outputDirValidator.test(outputDir))
            throw new ProjectOutputDirNotExistsException(String.format("Project output directory doesn't exits: ", outputDir));
        else
            projectFilePath = String.format("%s/%s", outputDir, Utils.getFullProjectNameWithPrefix(projectId));
        return projectFilePath;
    }

    class ProjectOutputDirNotExistsException extends RuntimeException {
        public ProjectOutputDirNotExistsException(String message) {
            super(message);
        }
    }
}
