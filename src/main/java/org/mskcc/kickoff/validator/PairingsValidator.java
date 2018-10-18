package org.mskcc.kickoff.validator;

import org.mskcc.domain.PairingInfo;

import java.util.List;

public interface PairingsValidator {
    boolean isValid(List<PairingInfo> pairingInfos);
}
