package org.mskcc.kickoff.domain;

import org.mskcc.kickoff.manifest.ManifestFile;

import java.util.Arrays;

public class RNASeqStrategy implements RequestTypeStrategy {
    @Override
    public void setRequiredFiles() {
        ManifestFile.setRequiredFiles(Arrays.asList(ManifestFile.MAPPING, ManifestFile.REQUEST, ManifestFile.CLINICAL));
    }
}
