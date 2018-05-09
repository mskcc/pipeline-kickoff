package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.util.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

public class PairingInfoValidPredicate implements BiPredicate<Sample, Sample> {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    @Override
    public boolean test(Sample tumor, Sample normal) {
        return isCompatible(tumor, normal);
    }

    private boolean isCompatible(Sample tumor, Sample normal) {
        List<InstrumentType> normalInstrumentTypes = getInstrumentTypes(normal);
        List<InstrumentType> tumorInstrumentTypes = getInstrumentTypes(tumor);

        boolean isCompatible = InstrumentType.isCompatible(normalInstrumentTypes, tumorInstrumentTypes);

        if (!isCompatible)
            DEV_LOGGER.warn(String.format("Different instruments type in pairing [normal sample %s: %s - tumor sample" +
                    " %s: %s]", normal.getIgoId(), normalInstrumentTypes, tumor.getIgoId(), tumorInstrumentTypes));
        else
            DEV_LOGGER.info(String.format("Instruments type in pairing [normal sample %s: %s - tumor sample %s: " +
                    "%s]", normal.getIgoId(), normalInstrumentTypes, tumor.getIgoId(), tumorInstrumentTypes));

        return isCompatible;
    }

    private List<InstrumentType> getInstrumentTypes(Sample sample) {
        if (isNaNormal(sample))
            return Arrays.asList(InstrumentType.ALL_COMPATIBLE_NA_NORMAL);

        List<InstrumentType> instrumentTypes = new ArrayList<>();

        List<String> seqNames = sample.getSeqNames();
        for (String seqName : seqNames) {
            InstrumentType instrumentTypeByName = InstrumentType.getInstrumentTypeByName(seqName);
            instrumentTypes.add(instrumentTypeByName);
        }

        return instrumentTypes;
    }

    private boolean isNaNormal(Sample sample) {
        return Objects.equals(sample.getIgoId(), Constants.NA_LOWER_CASE);
    }
}
