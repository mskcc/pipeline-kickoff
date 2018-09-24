package org.mskcc.kickoff.sampleset;

import org.apache.commons.lang3.StringUtils;
import org.mskcc.domain.Recipe;
import org.mskcc.domain.SampleSet;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.*;

public class SampleSetRetriever {
    private static final org.apache.log4j.Logger DEV_LOGGER = org.apache.log4j.Logger.getLogger(org.mskcc.util
            .Constants.DEV_LOGGER);

    private final SampleSetProxy sampleSetProxy;
    private final SamplesToRequestsConverter samplesToRequestsConverter;

    public SampleSetRetriever(SampleSetProxy sampleSetProxy, SamplesToRequestsConverter samplesToRequestsConverter) {
        this.sampleSetProxy = sampleSetProxy;
        this.samplesToRequestsConverter = samplesToRequestsConverter;
    }

    public KickoffSampleSet retrieve(String projectId, ProcessingType processingType) {
        try {
            KickoffSampleSet sampleSet = new KickoffSampleSet(projectId);

            putRequestsAndSamples(processingType, sampleSet);
            sampleSet.setPrimaryRequestId(sampleSetProxy.getPrimaryRequestId());

            validatePrimaryRequestIsInSampleSet(sampleSet);

            sampleSet.setBaitSet(sampleSetProxy.getBaitVersion());
            sampleSet.setRecipe(getRecipe());
            List<KickoffExternalSample> externalSamples = sampleSetProxy.getExternalSamples();
            DEV_LOGGER.info(String.format("Found %d external samples for sample set %s: %s", externalSamples.size(),
                    projectId, externalSamples));

            sampleSet.setExternalSamples(externalSamples);

            return sampleSet;
        } catch (Exception e) {
            throw new RuntimeException(String.format("Unable to retrieve Sample Set: %s. Cause: %s", projectId, e
                    .getMessage()), e);
        }
    }

    private void putRequestsAndSamples(ProcessingType processingType, KickoffSampleSet sampleSet) throws Exception {
        Collection<Sample> samples = sampleSetProxy.getIgoSamples();
        for (Sample sample : samples) {
            sampleSet.putSampleIfAbsent(sample);
        }

        putRequests(processingType, sampleSet, samples);
    }

    private void putRequests(ProcessingType processingType, KickoffSampleSet sampleSet, Collection<Sample> samples)
            throws Exception {
        List<KickoffRequest> kickoffRequests = new ArrayList<>();
        kickoffRequests.addAll(sampleSetProxy.getRequests(processingType));

        Collection<KickoffRequest> requestsFromSamples = convertToRequests(samples, processingType);
        kickoffRequests.addAll(requestsFromSamples);

        Map<String, KickoffRequest> requestIdToRequest = new HashMap<>();

        for (KickoffRequest kickoffRequest : kickoffRequests) {
            requestIdToRequest.put(kickoffRequest.getId(), kickoffRequest);
            sampleSet.putRequestIfAbsent(kickoffRequest);
        }

        sampleSet.setRequestIdToKickoffRequest(requestIdToRequest);
    }

    private void validatePrimaryRequestIsInSampleSet(KickoffSampleSet sampleSet) {
        if (!StringUtils.isEmpty(sampleSet.getPrimaryRequestId()) && !sampleSet.getRequestIdToKickoffRequest()
                .containsKey(sampleSet.getPrimaryRequestId()))
            throw new SampleSet.PrimaryRequestNotPartOfSampleSetException(String.format("Primary request %s is not " +
                    "part of sample set  %s", sampleSet.getPrimaryRequestId(), sampleSet.getName()));
    }

    private Recipe getRecipe() throws Exception {
        return Recipe.getRecipeByValue(sampleSetProxy.getRecipe());
    }

    private Collection<KickoffRequest> convertToRequests(Collection<Sample> samples, ProcessingType processingType)
            throws Exception {
        return samplesToRequestsConverter.convert(samples, processingType).values();
    }
}
