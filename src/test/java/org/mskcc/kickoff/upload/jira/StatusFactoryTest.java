package org.mskcc.kickoff.upload.jira;

import org.hamcrest.object.IsCompatibleType;
import org.junit.Test;
import org.mskcc.kickoff.upload.jira.state.*;
import org.mskcc.util.TestUtils;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class StatusFactoryTest {
    private final String regenerateState = "regenerateState";
    private final String generatedState = "generatedState";
    private final String generateState = "generateState";

    private final FilesGeneratedStatus filesGeneratedState = new FilesGeneratedStatus(generatedState);
    private final RegenerateFilesStatus regenerateFilesState = new RegenerateFilesStatus(regenerateState,
            "regeneratedTransition", filesGeneratedState);
    private final GenerateFilesStatus generateFilesState = new GenerateFilesStatus(generateState,
            "generatedTransition", filesGeneratedState);
    private final StatusFactory statusFactory = new StatusFactory(regenerateFilesState, generateFilesState,
            filesGeneratedState);

    @Test
    public void whenInRegenerateState_shouldReturnRegenerateState() throws Exception {
        IssueStatus jiraState = statusFactory.getStatus(regenerateState);

        assertThat(jiraState.getName(), is(regenerateState));
        assertThat(jiraState.getClass(), IsCompatibleType.typeCompatibleWith(RegenerateFilesStatus.class));
    }

    @Test
    public void whenInGenerateState_shouldReturnRegenerateState() throws Exception {
        IssueStatus jiraState = statusFactory.getStatus(regenerateState);

        assertThat(jiraState.getName(), is(regenerateState));
        assertThat(jiraState.getClass(), IsCompatibleType.typeCompatibleWith(RegenerateFilesStatus.class));
    }

    @Test
    public void whenInGeneratedState_shouldReturnGeneratedState() throws Exception {
        IssueStatus jiraState = statusFactory.getStatus(generatedState);

        assertThat(jiraState.getName(), is(generatedState));
        assertThat(jiraState.getClass(), IsCompatibleType.typeCompatibleWith(FilesGeneratedStatus.class));
    }

    @Test
    public void whenUnsupportedState_shouldThrowException() throws Exception {
        Optional<Exception> exception = TestUtils.assertThrown(() -> statusFactory.getStatus
                ("somethingElse"));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(IllegalArgumentException.class));
    }

}