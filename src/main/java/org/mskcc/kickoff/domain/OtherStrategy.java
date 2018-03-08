package org.mskcc.kickoff.domain;

import org.mskcc.kickoff.manifest.ManifestFile;

import java.util.Arrays;

public class OtherStrategy implements RequestTypeStrategy {
    @Override
    public void setRequiredFiles() {
        ManifestFile.setRequiredFiles(Arrays.asList(ManifestFile.CLINICAL, ManifestFile.MAPPING, ManifestFile.REQUEST));
    }
}
