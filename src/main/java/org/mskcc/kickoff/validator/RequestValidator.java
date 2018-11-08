package org.mskcc.kickoff.validator;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Component
public class RequestValidator {
    @Autowired
    private List<Predicate<KickoffRequest>> validators = new ArrayList<>();

    public void validate(KickoffRequest kickoffRequest) {
        for (Predicate<KickoffRequest> validator : validators)
            validator.test(kickoffRequest);
    }

    public List<Predicate<KickoffRequest>> getValidators() {
        return validators;
    }
}
