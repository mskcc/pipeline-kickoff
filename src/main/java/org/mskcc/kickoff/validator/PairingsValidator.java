package org.mskcc.kickoff.validator;

import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.sample.Sample;

import java.util.List;
import java.util.function.BiPredicate;

class PairingsValidator {
    private final BiPredicate<Sample, Sample> pairingInfoPredicate;

    PairingsValidator(BiPredicate<Sample, Sample> pairingInfoPredicate) {
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
