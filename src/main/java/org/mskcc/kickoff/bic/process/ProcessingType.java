package org.mskcc.kickoff.bic.process;

import org.mskcc.kickoff.bic.domain.KickoffRequest;
import org.mskcc.kickoff.bic.domain.Pool;
import org.mskcc.kickoff.bic.domain.Run;
import org.mskcc.kickoff.bic.domain.sample.Sample;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

public interface ProcessingType {
    void archiveFilesToOld(KickoffRequest request);

    Map<String, Sample> getAllValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate);

    String getIncludeRunId(Collection<Run> runs);

    Map<String, Pool> getValidPools(Map<String, Pool> pools);
}
