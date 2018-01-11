package org.mskcc.kickoff.domain;

import org.mskcc.kickoff.manifest.ManifestFile;

import java.util.Arrays;

public class ImpactStrategy implements RequestTypeStrategy {
    @Override
    public void setRequiredFiles() {
        ManifestFile.setRequiredFiles(Arrays.asList(
                ManifestFile.GROUPING,
                ManifestFile.PAIRING,
                ManifestFile.MAPPING,
                ManifestFile.REQUEST
        ));
    }
}
