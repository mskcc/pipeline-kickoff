package org.mskcc.kickoff.roslin.printer;

import org.mskcc.kickoff.roslin.domain.KickoffRequest;
import org.mskcc.kickoff.roslin.printer.observer.ManifestFileObserver;
import org.mskcc.kickoff.roslin.printer.observer.ObserverManager;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class FilePrinter {
    protected final ObserverManager observerManager;

    @Autowired
    protected FilePrinter(ObserverManager observerManager) {
        this.observerManager = observerManager;
    }

    public abstract boolean shouldPrint(KickoffRequest kickoffRequest);

    public abstract String getFilePath(KickoffRequest request);

    public abstract void print(KickoffRequest kickoffRequest);

    public void register(ManifestFileObserver manifestFileObserver) {
        observerManager.register(manifestFileObserver);
    }
}
