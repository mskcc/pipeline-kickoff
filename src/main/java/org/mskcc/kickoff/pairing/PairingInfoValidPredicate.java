package org.mskcc.kickoff.pairing;

import org.apache.log4j.Logger;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;
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

        return InstrumentType.isCompatible(normalInstrumentTypes, tumorInstrumentTypes);
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
