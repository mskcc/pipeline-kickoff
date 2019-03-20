package org.mskcc.kickoff.factory;

import org.mskcc.kickoff.config.Arguments;
import org.mskcc.kickoff.config.SpringProfile;
import org.mskcc.kickoff.roslin.lims.CreateManifestSheet;

public class ManifestSheetFactory {

    public static void main(String[] args) {
        Arguments.parseArguments(args);

        String pipeline = Arguments.pipeline;

        if (pipeline.equals(SpringProfile.BIC)) {
          org.mskcc.kickoff.bic.lims.CreateManifestSheet.main(args);
        } else if (pipeline.equals(SpringProfile.ROSLIN)) {
            CreateManifestSheet.main(args);
        } else {
            throw new RuntimeException("Pipeline not recognized: " + pipeline);
        }
    }

}
