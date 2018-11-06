package org.mskcc.kickoff.validator;

import org.mskcc.domain.Strand;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class StrandValidator implements Predicate<KickoffRequest> {
    private ErrorRepository errorRepository;

    @Autowired
    public StrandValidator(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean test(KickoffRequest kickoffRequest) {
        kickoffRequest.addStrand(Strand.NONE);
        kickoffRequest.addStrand(Strand.REVERSE);
        if (kickoffRequest.getStrands().size() > 1) {
            String msg = String.format("Ambiguous strand for request %s: %s", kickoffRequest.getId(), kickoffRequest
                    .getStrands());
            errorRepository.add(new GenerationError(msg, ErrorCode.AMBIGUOUS_STRAND));
            return false;
        }

        return true;
    }
}
