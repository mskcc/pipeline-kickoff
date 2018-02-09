package org.mskcc.kickoff.printer.observer;

import org.junit.Test;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.FileGenerated;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.process.ProcessingType;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class FileGenerationStatusManifestFileObserverTest {
    private final KickoffRequest request = new KickoffRequest("req1", mock(ProcessingType.class));
    private FileGenerationStatusManifestFileObserver fileGenerationStatusManifestFileObserver = new
            FileGenerationStatusManifestFileObserver();

    @Test
    public void whenUpdateIfInvokeWithFileGeneratedEvent_shouldSetManifestFileIsGeneratedToTrue() {
        ManifestFile manifestFile = ManifestFile.GROUPING;

        fileGenerationStatusManifestFileObserver.update(request, manifestFile, FileGenerated.INSTANCE);

        assertThat(manifestFile.isFileGenerated(), is(true));
    }

    @Test
    public void whenUpdateIfInvokeWithErrorEvent_shouldAddErrorsToFilesList() throws Exception {
        ManifestFile manifestFile = ManifestFile.GROUPING;
        String error1 = "blabla ale błąd";
        String error2 = "blabla ale błąd nawet kolejny";

        fileGenerationStatusManifestFileObserver.update(request, manifestFile, GenerationError.INSTANCE, error1);
        fileGenerationStatusManifestFileObserver.update(request, manifestFile, GenerationError.INSTANCE, error2);

        assertThat(manifestFile.getGenerationErrors(), is(Arrays.asList(error1, error2)));
    }


}