package org.mskcc.kickoff.upload;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.springframework.stereotype.Component;

@Component
public class RequiredFilesValidator implements FilesValidator {
    @Override
    public boolean isValid(String issueId) {
        return allRequiredFilesGenerated() && filesHaveNoErrors();
    }

    private boolean allRequiredFilesGenerated() {
        return ManifestFile.getRequiredFiles().stream()
                .allMatch(ManifestFile::isFileGenerated);
    }

    private boolean filesHaveNoErrors() {
        return ManifestFile.getRequiredFiles().stream()
                .allMatch(r -> r.getGenerationErrors().size() == 0);
    }
}
