package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.util.Constants;

import java.util.Objects;
import java.util.function.BiPredicate;

public class PairingInfoValidPredicate implements BiPredicate<Sample, Sample> {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private ErrorRepository errorRepository;

    public PairingInfoValidPredicate(ErrorRepository errorRepository) {
        this.errorRepository = errorRepository;
    }

    @Override
    public boolean test(Sample tumor, Sample normal) {
        return isCompatible(tumor, normal);
    }

    private boolean isCompatible(Sample tumor, Sample normal) {
        InstrumentType normalInstrumentType = getInstrumentType(normal);
        InstrumentType tumorInstrumentType = getInstrumentType(tumor);

        boolean isCompatible = normalInstrumentType.isCompatibleWith(tumorInstrumentType);

        if (!isCompatible) {
            String message = String.format("Different instruments type in pairing [normal sample %s: %s - tumor " +
                    "sample" +
                    " %s: %s]", normal.getIgoId(), normalInstrumentType, tumor.getIgoId(), tumorInstrumentType);
            DEV_LOGGER.warn(message);
            errorRepository.add(new GenerationError(message, ErrorCode.INCOMPATIBLE_INSTRUMENT_TYPES));
        } else {
            DEV_LOGGER.info(String.format("Instruments type in pairing [normal sample %s: %s - tumor sample %s: " +
                    "%s]", normal.getIgoId(), normalInstrumentType, tumor.getIgoId(), tumorInstrumentType));
        }

        return isCompatible;
    }

    private InstrumentType getInstrumentType(Sample sample) {
        if (isNaNormal(sample))
            return InstrumentType.ALL_COMPATIBLE_NA_NORMAL;

        String seqName = sample.getSeqName();
        return InstrumentType.getInstrumentTypeByName(seqName);
    }

    private boolean isNaNormal(Sample sample) {
        return Objects.equals(sample.getIgoId(), Constants.NA_LOWER_CASE);
    }
}
