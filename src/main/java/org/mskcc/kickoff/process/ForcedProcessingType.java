package org.mskcc.kickoff.process;

import org.mskcc.domain.Pool;
import org.mskcc.domain.Run;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.Request;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.util.CommonUtils;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

public class ForcedProcessingType implements ProcessingType {
    @Override
    public void archiveFilesToOld(Request request) {

    }

    @Override
    public Map<String, Sample> getAllValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate) {
        return samples.entrySet()
                .stream()
                .filter(s -> samplePredicate.test(s.getValue()))
                .collect(CommonUtils.getLinkedHashMapCollector());
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
