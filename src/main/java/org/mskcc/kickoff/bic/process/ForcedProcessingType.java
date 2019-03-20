package org.mskcc.kickoff.bic.process;

import org.mskcc.kickoff.bic.domain.Pool;
import org.mskcc.kickoff.bic.domain.Run;
import org.mskcc.kickoff.bic.domain.sample.Sample;
import org.mskcc.kickoff.bic.domain.KickoffRequest;
import org.mskcc.kickoff.bic.util.Constants;
import org.mskcc.kickoff.bic.util.Utils;

import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;

public class ForcedProcessingType implements ProcessingType {
    @Override
    public void archiveFilesToOld(KickoffRequest request) {

    }

    @Override
    public Map<String, Sample> getAllValidSamples(Map<String, Sample> samples, Predicate<Sample> samplePredicate) {
        return samples.entrySet()
                .stream()
                .filter(s -> samplePredicate.test(s.getValue()))
                .collect(Utils.getLinkedHashMapCollector());
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
