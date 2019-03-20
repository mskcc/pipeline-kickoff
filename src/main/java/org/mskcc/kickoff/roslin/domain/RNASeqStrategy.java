package org.mskcc.kickoff.roslin.domain;

import org.mskcc.kickoff.roslin.manifest.ManifestFile;

import java.util.Arrays;

public class RNASeqStrategy implements RequestTypeStrategy {
    @Override
    public void setRequiredFiles() {
        ManifestFile.setRequiredFiles(Arrays.asList(ManifestFile.MAPPING, ManifestFile.REQUEST, ManifestFile.CLINICAL));
    }
}
