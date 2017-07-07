package org.mskcc.kickoff.process;

import org.mskcc.domain.Pool;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

public interface ProcessingType {
    void archiveFilesToOld(Request request);

    Map<String, Sample> getAllValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate);

    String getIncludeRunId(Collection<Run> runs);

    Map<String, Pool> getValidPools(Map<String, Pool> pools);
}
