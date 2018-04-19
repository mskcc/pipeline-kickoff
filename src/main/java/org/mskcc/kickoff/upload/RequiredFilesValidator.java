package org.mskcc.kickoff.upload;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.springframework.stereotype.Component;

@Component
public class RequiredFilesValidator implements FilesValidator {
    private ErrorRepository errorRepository;

    public RequiredFilesValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean isValid(String issueId) {
        return allRequiredFilesGenerated() && filesHaveNoErrors() && noGeneralErrors();
    }

    private boolean allRequiredFilesGenerated() {
        return ManifestFile.getRequiredFiles().stream()
                .allMatch(ManifestFile::isFileGenerated);
    }

    private boolean filesHaveNoErrors() {
        return ManifestFile.getRequiredFiles().stream()
                .allMatch(r -> r.getGenerationErrors().size() == 0);
    }

    private boolean noGeneralErrors() {
        return errorRepository.getErrors().size() == 0;
    }
}
