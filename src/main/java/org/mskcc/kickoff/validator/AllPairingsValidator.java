package org.mskcc.kickoff.validator;

import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.sample.Sample;

import java.util.List;
import java.util.function.BiPredicate;

public class AllPairingsValidator implements PairingsValidator {
    private final BiPredicate<Sample, Sample> pairingInfoPredicate;

    public AllPairingsValidator(BiPredicate<Sample, Sample> pairingInfoPredicate) {
        this.pairingInfoPredicate = pairingInfoPredicate;
    }

    @Override
    public boolean isValid(List<PairingInfo> pairingInfos) {
        boolean isValid = true;
        for (PairingInfo pairingInfo : pairingInfos) {
            isValid = isValid && pairingInfoPredicate.test(pairingInfo.getTumor(), pairingInfo.getNormal());
        }

        return isValid;
    }
}
