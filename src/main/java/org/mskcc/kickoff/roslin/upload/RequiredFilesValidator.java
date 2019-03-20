package org.mskcc.kickoff.roslin.upload;

import org.mskcc.kickoff.roslin.manifest.ManifestFile;
import org.mskcc.kickoff.roslin.validator.ErrorRepository;
import org.springframework.beans.factory.annotation.Autowired;

public class RequiredFilesValidator implements FilesValidator {
    private ErrorRepository errorRepository;

    @Autowired
    public RequiredFilesValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean isValid(String issueId) {
        return allRequiredFilesGenerated() &&
                filesHaveNoErrors() &&
                noGeneralErrors();
    }

    private boolean allRequiredFilesGenerated() {
        return ManifestFile.getRequiredFiles().stream()
                .allMatch(ManifestFile::isFileGenerated);
    }

    private boolean filesHaveNoErrors() {
        return ManifestFile.getRequiredFiles().stream()
                .allMatch(r -> r.getGenerationErrors().isEmpty());
    }

    private boolean noGeneralErrors() {
        return errorRepository.getErrors().isEmpty();
    }
}
