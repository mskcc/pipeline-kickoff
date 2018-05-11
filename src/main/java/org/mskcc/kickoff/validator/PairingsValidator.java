package org.mskcc.kickoff.validator;

import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.sample.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiPredicate;

@Component
class PairingsValidator {
    private final BiPredicate<Sample, Sample> pairingInfoPredicate;

    @Autowired
    PairingsValidator(@Qualifier("pairingInfoValidPredicate") BiPredicate<Sample, Sample> pairingInfoPredicate) {
        this.pairingInfoPredicate = pairingInfoPredicate;
    }

    public boolean isValid(List<PairingInfo> pairingInfos) {
        boolean isValid = true;
        for (PairingInfo pairingInfo : pairingInfos) {
            isValid = isValid && pairingInfoPredicate.test(pairingInfo.getTumor(), pairingInfo.getNormal());
        }

        return isValid;
    }
}
