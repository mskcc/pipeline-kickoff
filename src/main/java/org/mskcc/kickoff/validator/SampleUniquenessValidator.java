package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.util.Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class SampleUniquenessValidator implements Predicate<KickoffRequest> {
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private ErrorRepository errorRepository;

    @Autowired
    public SampleUniquenessValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        Set<String> duplicateSamples = getDuplicateSamples(kickoffRequest);
        if (hasDuplicateSamples(duplicateSamples)) {
            duplicateSamples.forEach(s -> {
                String message = String.format("This request has two samples that have the same name: %s", s);
                PM_LOGGER.error(message);
                DEV_LOGGER.error(message);
                errorRepository.add(new GenerationError(String.format("This request has two samples that have the " +
                        "same name: %s", s), ErrorCode.DUPLICATE_SAMPLE));
            });
            Utils.setExitLater(true);
            return true;
        }

        return false;
    }

    private Set<String> getDuplicateSamples(KickoffRequest kickoffRequest) {
        Set<String> uniqueCmoSampleIds = new HashSet<>();

        return kickoffRequest.getValidNonPooledNormalSamples().values().stream()
                .map(Sample::getCmoSampleId)
                .filter(cmoSampleId -> !uniqueCmoSampleIds.add(cmoSampleId))
                .collect(Collectors.toSet());
    }

    private boolean hasDuplicateSamples(Set<String> duplicateSamples) {
        return duplicateSamples.size() > 0;
    }
}
