package org.mskcc.kickoff.printer.observer;

import org.junit.Test;
import org.mskcc.kickoff.manifest.ManifestFile;
import org.mskcc.kickoff.notify.GenerationError;
import org.mskcc.kickoff.printer.ErrorCode;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.mskcc.kickoff.validator.InMemoryErrorRepository;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class FileGenerationStatusManifestFileObserverTest {
    private ErrorRepository errorRepository = new InMemoryErrorRepository();

    private FileGenerationStatusManifestFileObserver fileGenerationStatusManifestFileObserver = new
            FileGenerationStatusManifestFileObserver(errorRepository);

    @Test
    public void whenUpdateIfInvokeWithFileGeneratedEvent_shouldSetManifestFileIsGeneratedToTrue() {
        ManifestFile manifestFile = ManifestFile.GROUPING;

        fileGenerationStatusManifestFileObserver.updateGenerationStatus(manifestFile);

        assertThat(manifestFile.isFileGenerated(), is(true));
    }

    @Test
    public void whenUpdateIfInvokeWithErrorEvent_shouldAddErrorsToFilesList() throws Exception {
        ManifestFile manifestFile = ManifestFile.GROUPING;
        GenerationError error1 = new GenerationError("blabla ale błąd", ErrorCode.UNMATCHED_NORMAL);
        GenerationError error2 = new GenerationError("blabla ale błąd nawet kolejny", ErrorCode.UNMATCHED_TUMOR);

        fileGenerationStatusManifestFileObserver.updateFileError(manifestFile, error1);
        fileGenerationStatusManifestFileObserver.updateFileError(manifestFile, error2);

        assertThat(manifestFile.getGenerationErrors(), is(Arrays.asList(error1, error2)));
    }


}