package org.mskcc.kickoff.process;

import org.mskcc.domain.Pool;
import org.mskcc.domain.Run;
import org.mskcc.domain.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ForcedProcessingType implements ProcessingType {
    @Override
    public void archiveFilesToOld(Request request) {

    }

    @Override
    public Map<String, Sample> getAllValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate) {
        return samples.entrySet()
                .stream()
                .filter(s -> samplePredicate.test(s.getValue()))
                .collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue(), (u, v) -> {
                    throw new IllegalStateException(String.format("Duplicate key %s", u));
                }, LinkedHashMap::new));
    }

    @Override
    public String getIncludeRunId(Collection<Run> runs) {
        return Constants.FORCED;
    }

    @Override
    public Map<String, Pool> getValidPools(Map<String, Pool> pools) {
        return pools;
    }
}
