package org.mskcc.kickoff.upload.jira;

import org.hamcrest.object.IsCompatibleType;
import org.junit.Test;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.util.TestUtils;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class FilesGeneratedStateTest {
    @Test
    public void whenTryToUploadFilesInFilesGeneratedState_shouldThrowException() throws Exception {
        FilesGeneratedState filesGeneratedState = new FilesGeneratedState("something");

        Optional<Exception> exception = TestUtils.assertThrown(() -> filesGeneratedState.uploadFiles(mock
                (KickoffRequest.class), mock(JiraFileUploader.class)));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(IllegalStateException.class));
    }
}