package org.mskcc.kickoff.roslin.domain;

import org.mskcc.kickoff.roslin.manifest.ManifestFile;

import java.util.Arrays;

public class ImpactStrategy implements RequestTypeStrategy {
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
