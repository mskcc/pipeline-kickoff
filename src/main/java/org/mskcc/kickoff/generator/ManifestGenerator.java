package org.mskcc.kickoff.generator;

import org.mskcc.kickoff.domain.KickoffRequest;

public interface ManifestGenerator {
    void generate(KickoffRequest kickoffRequest) throws Exception;
}
