package org.mskcc.kickoff.printer.observer;

import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.FileGenerated;
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

    public void notifyObserversOfError(KickoffRequest request, ManifestFile manifestFileType, String errorMessage,
                                       GenerationError generationError) {
        for (ManifestFileObserver manifestFileObserver : manifestFileObservers) {
            manifestFileObserver.update(request, manifestFileType, generationError, errorMessage);
        }
    }

    public void notifyObserversOfFileCreated(KickoffRequest request, ManifestFile manifestFileType) {
        for (ManifestFileObserver manifestFileObserver : manifestFileObservers) {
            manifestFileObserver.update(request, manifestFileType, FileGenerated.INSTANCE);
        }
    }

    public void register(ManifestFileObserver manifestFileObserver) {
        manifestFileObservers.add(manifestFileObserver);
    }

    public void unregister(ManifestFileObserver manifestFileObserver) {
        manifestFileObservers.remove(manifestFileObserver);
    }
}
