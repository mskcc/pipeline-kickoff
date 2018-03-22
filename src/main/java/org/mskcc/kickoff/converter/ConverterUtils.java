package org.mskcc.kickoff.converter;

import org.apache.commons.lang3.StringUtils;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.domain.KickoffSampleSet;
import org.mskcc.kickoff.util.Utils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ConverterUtils {
    static <T> T getSameForAllRequestProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, T> requestProperty,
                                              String propertyName) {
        return getSameForAllRequestProperty(sampleSet, requestProperty, propertyName, Objects::nonNull);
    }

    static <T> T getSameForAllRequestProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, T> requestProperty,
                                              String propertyName, Predicate<T> notEmptyPredicate) {
        List<T> uniqueRequestsProperty = sampleSet.getKickoffRequests().stream()
                .map(requestProperty)
                .distinct()
                .collect(Collectors.toList());

        if (uniqueRequestsProperty.size() > 1)
            throw new SampleSetToRequestConverter.AmbiguousPropertyException(String.format("Ambiguous %s for project:" +
                    " %s: %s", propertyName, sampleSet.getName(), StringUtils.join(uniqueRequestsProperty, ",")));

        if (uniqueRequestsProperty.size() == 1 && notEmptyPredicate.test(uniqueRequestsProperty.get(0))) {
            return uniqueRequestsProperty.get(0);
        }

        throw new SampleSetToRequestConverter.NoPropertySetException(String.format("Project: %s has no %s set",
                sampleSet.getName(), propertyName));
    }

    static String getMergedPropertyValue(KickoffSampleSet sampleSet, Function<KickoffRequest, String> requestProperty,
                                         String delimiter) {
        return sampleSet.getKickoffRequests().stream()
                .map(requestProperty)
                .filter(p -> !StringUtils.isEmpty(p))
                .distinct()
                .collect(Collectors.joining(delimiter));
    }

    static String getArbitraryPropertyValue(KickoffSampleSet sampleSet, String projectInfoProperty) {
        return sampleSet.getKickoffRequests().get(0).getProjectInfo().get(projectInfoProperty);
    }

    static String getJoinedRequestProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, String>
            requestProperty) {
        return getJoinedRequestProperty(sampleSet, requestProperty, Utils.DEFAULT_DELIMITER);
    }

    static String getJoinedRequestProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, String> requestProperty,
                                           String delimiter) {
        return sampleSet.getKickoffRequests().stream()
                .filter(r -> !StringUtils.isEmpty(requestProperty.apply(r)))
                .map(r -> String.format("%s: %s", r.getId(), requestProperty.apply(r)))
                .collect(Collectors.joining(delimiter));
    }

    static <T> Optional<T> getOptionalProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, T> requestProperty,
                                               String propertyName) {
        if (anyRequestContainsProperty(sampleSet, propertyName))
            return Optional.of(getSameForAllRequestProperty(sampleSet, requestProperty, propertyName));
        return Optional.empty();
    }

    private static boolean anyRequestContainsProperty(KickoffSampleSet sampleSet, String projectInfoProperty) {
        return sampleSet.getKickoffRequests().stream()
                .filter(r -> r.getProjectInfo().containsKey(projectInfoProperty)).count() > 0;
    }

    public static <T> T getRequiredSameForAllProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, T>
            requestProperty, String propertyName, Predicate<T> nonEmptyPredicate) {
        List<String> requestsWithNullProperty = sampleSet.getKickoffRequests().stream()
                .filter(r -> requestProperty.apply(r) == null)
                .map(r -> r.getId())
                .collect(Collectors.toList());

        if (requestsWithNullProperty.size() > 0)
            throw new RequiredPropertyNotSetException(String.format("Required field: %s not set for requests: %s",
                    propertyName, StringUtils.join(requestsWithNullProperty)));

        return getSameForAllRequestProperty(sampleSet, requestProperty, propertyName, nonEmptyPredicate);
    }

    public static <T> T getRequiredSameForAllProperty(KickoffSampleSet sampleSet, Function<KickoffRequest, T>
            requestProperty, String propertyName) {
        return getRequiredSameForAllProperty(sampleSet, requestProperty, propertyName, Objects::nonNull);
    }

    static class RequiredPropertyNotSetException extends RuntimeException {
        public RequiredPropertyNotSetException(String message) {
            super(message);
        }
    }
}


