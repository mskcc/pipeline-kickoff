package org.mskcc.kickoff.velox;

import org.mskcc.domain.Recipe;
import org.mskcc.domain.external.ExternalSample;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.SampleSet;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class SampleSetRetriever {
    private static final org.apache.log4j.Logger DEV_LOGGER = org.apache.log4j.Logger.getLogger(org.mskcc.util
            .Constants.DEV_LOGGER);

    private final SampleSetProxy sampleSetProxy;
    private final SamplesToRequestsConverter samplesToRequestsConverter;

    public SampleSetRetriever(SampleSetProxy sampleSetProxy, SamplesToRequestsConverter samplesToRequestsConverter) {
        this.sampleSetProxy = sampleSetProxy;
        this.samplesToRequestsConverter = samplesToRequestsConverter;
    }

    public SampleSet retrieve(String projectId, ProcessingType processingType) {
        try {
            SampleSet sampleSet = new SampleSet(projectId);

            sampleSet.setRequests(getRequests(processingType));
            sampleSet.setPrimaryRequestId(sampleSetProxy.getPrimaryRequestId());
            sampleSet.setBaitSet(sampleSetProxy.getBaitVersion());
            sampleSet.setRecipe(getRecipe());
            List<ExternalSample> externalSamples = sampleSetProxy.getExternalSamples();
            DEV_LOGGER.info(String.format("Found %d external samples for sample set %s: %s", externalSamples.size(),
                    projectId, externalSamples));

            sampleSet.setExternalSamples(externalSamples);

            return sampleSet;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to retrieve Sample Set: %s", projectId), e);
        }
    }

    private Recipe getRecipe() throws Exception {
        return Recipe.getRecipeByValue(sampleSetProxy.getRecipe());
    }

    private List<KickoffRequest> getRequests(ProcessingType processingType) throws Exception {
        List<KickoffRequest> kickoffRequests = new ArrayList<>();
        kickoffRequests.addAll(sampleSetProxy.getRequests(processingType));
        kickoffRequests.addAll(convertToRequests(sampleSetProxy.getIgoSamples(), processingType));

        return kickoffRequests;
    }

    private Collection<KickoffRequest> convertToRequests(Collection<Sample> samples, ProcessingType processingType)
            throws Exception {
        return samplesToRequestsConverter.convert(samples, processingType).values();
    }
}
