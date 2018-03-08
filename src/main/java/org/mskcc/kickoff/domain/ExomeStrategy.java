package org.mskcc.kickoff.domain;

import org.mskcc.kickoff.manifest.ManifestFile;

import java.util.Arrays;

public class ExomeStrategy implements RequestTypeStrategy {
    @Override
    public void setRequiredFiles() {
        ManifestFile.setRequiredFiles(Arrays.asList(
                ManifestFile.CLINICAL,
                ManifestFile.GROUPING,
                ManifestFile.PAIRING,
                ManifestFile.MAPPING,
                ManifestFile.REQUEST
        ));
    }
}
