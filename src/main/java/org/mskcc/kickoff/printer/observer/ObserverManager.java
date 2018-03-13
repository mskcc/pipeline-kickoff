package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ObserverManager {
    private List<ManifestFileObserver> manifestFileObservers = new ArrayList<>();

    public void notifyObserversOfError(ManifestFile manifestFileType, GenerationError generationError) {
        for (ManifestFileObserver manifestFileObserver : manifestFileObservers) {
            manifestFileObserver.updateFileError(manifestFileType, generationError);
        }
    }

    public void notifyObserversOfFileCreated(ManifestFile manifestFileType) {
        for (ManifestFileObserver manifestFileObserver : manifestFileObservers) {
            manifestFileObserver.updateGenerationStatus(manifestFileType);
        }
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        manifestFileObservers.add(manifestFileObserver);
    }

    public void unregister(ManifestFileObserver manifestFileObserver) {
        manifestFileObservers.remove(manifestFileObserver);
    }
}
