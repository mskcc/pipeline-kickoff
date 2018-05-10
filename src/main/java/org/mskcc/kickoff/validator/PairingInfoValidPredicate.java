package org.mskcc.kickoff.validator;

import org.apache.log4j.Logger;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiPredicate;

@Component
@Qualifier("pairingInfoValidPredicate")
public class PairingInfoValidPredicate implements BiPredicate<Sample, Sample> {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private final ObserverManager observerManager;

    @Autowired
    public PairingInfoValidPredicate(ObserverManager observerManager) {
        this.observerManager = observerManager;
    }

    @Override
    public boolean test(Sample tumor, Sample normal) {
        List<InstrumentType> normalInstrumentTypes = normal.getInstrumentTypes();
        List<InstrumentType> tumorInstrumentTypes = tumor.getInstrumentTypes();

        boolean isCompatible = InstrumentType.isCompatible(normalInstrumentTypes, tumorInstrumentTypes);

        if (!isCompatible) {
            String message = String.format("Different instruments type in pairing [normal sample %s: %s - tumor " +
                    "sample" +
                    " %s: %s]", normal.getIgoId(), normalInstrumentTypes, tumor.getIgoId(), tumorInstrumentTypes);
            DEV_LOGGER.warn(message);
            observerManager.notifyObserversOfError(ManifestFile.PAIRING, new GenerationError(message, ErrorCode
                    .PAIRING_SEQUENCERS_NOT_COMPATIBLE));
        } else
            DEV_LOGGER.info(String.format("Instruments type in pairing [normal sample %s: %s - tumor sample %s: " +
                    "%s]", normal.getIgoId(), normalInstrumentTypes, tumor.getIgoId(), tumorInstrumentTypes));

        return isCompatible;
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
