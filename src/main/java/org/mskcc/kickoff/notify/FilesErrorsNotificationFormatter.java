package org.mskcc.kickoff.notify;

import org.apache.commons.lang.text.StrBuilder;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Collectors;

public class FilesErrorsNotificationFormatter implements NotificationFormatter {
    private ErrorRepository errorRepository;
    private NewLineStrategy newLineStrategy;

    @Autowired
    public FilesErrorsNotificationFormatter(ErrorRepository errorRepository, NewLineStrategy newLineStrategy) {
        this.errorRepository = errorRepository;
        this.newLineStrategy = newLineStrategy;
    }

    @Override
    public String format() {
        StrBuilder errorComment = new StrBuilder();

        if (!errorRepository.getErrors().isEmpty()) {
            errorComment.append("Errors:");
            errorComment.append(newLineStrategy.getNewLineSeparator());
            for (GenerationError generationError : errorRepository.getErrors()) {
                errorComment.append(String.format("    -%s", generationError.getMessage()));
                errorComment.append(newLineStrategy.getNewLineSeparator());
            }

            errorComment.append(newLineStrategy.getNewLineSeparator());
        }

        for (ManifestFile manifestFile : ManifestFile.getRequiredFiles()) {
            if (!manifestFile.isFileGenerated()) {
                errorComment.append(String.format("Required file not created: %s", manifestFile.getName()));
                errorComment.append(newLineStrategy.getNewLineSeparator());
            }

            if (manifestFile.getGenerationErrors().size() > 0) {
                errorComment.append(String.format("%s errors:", manifestFile.getName()));
                errorComment.append(newLineStrategy.getNewLineSeparator());
                errorComment.append(manifestFile.getGenerationErrors().stream()
                        .map(e -> String.format("    -%s%s", e.getMessage(), newLineStrategy.getNewLineSeparator()))
                        .collect(Collectors.joining()));
            }
        }

        return errorComment.toString();
    }
}
