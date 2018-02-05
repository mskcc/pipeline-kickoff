package org.mskcc.kickoff.upload.jira;

import org.hamcrest.object.IsCompatibleType;
import org.junit.Test;
import org.mskcc.kickoff.upload.jira.state.*;
import org.mskcc.util.TestUtils;

import java.util.Optional;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class JiraStateFactoryTest {
    private final String regenerateState = "regenerateState";
    private final String generatedState = "generatedState";
    private final String generateState = "generateState";

    private final FilesGeneratedState filesGeneratedState = new FilesGeneratedState(generatedState);
    private final RegenerateFilesState regenerateFilesState = new RegenerateFilesState(regenerateState,
            "regeneratedTransition", filesGeneratedState);
    private final GenerateFilesState generateFilesState = new GenerateFilesState(generateState,
            "generatedTransition", filesGeneratedState);
    private final JiraStateFactory jiraStateFactory = new JiraStateFactory(regenerateFilesState, generateFilesState,
            filesGeneratedState);

    @Test
    public void whenInRegenerateState_shouldReturnRegenerateState() throws Exception {
        JiraIssueState jiraState = jiraStateFactory.getJiraState(regenerateState);

        assertThat(jiraState.getName(), is(regenerateState));
        assertThat(jiraState.getClass(), IsCompatibleType.typeCompatibleWith(RegenerateFilesState.class));
    }

    @Test
    public void whenInGenerateState_shouldReturnRegenerateState() throws Exception {
        JiraIssueState jiraState = jiraStateFactory.getJiraState(regenerateState);

        assertThat(jiraState.getName(), is(regenerateState));
        assertThat(jiraState.getClass(), IsCompatibleType.typeCompatibleWith(RegenerateFilesState.class));
    }

    @Test
    public void whenInGeneratedState_shouldReturnGeneratedState() throws Exception {
        JiraIssueState jiraState = jiraStateFactory.getJiraState(generatedState);

        assertThat(jiraState.getName(), is(generatedState));
        assertThat(jiraState.getClass(), IsCompatibleType.typeCompatibleWith(FilesGeneratedState.class));
    }

    @Test
    public void whenUnsupportedState_shouldThrowException() throws Exception {
        Optional<Exception> exception = TestUtils.assertThrown(() -> jiraStateFactory.getJiraState
                ("somethingElse"));

        assertThat(exception.isPresent(), is(true));
        assertThat(exception.get().getClass(), IsCompatibleType.typeCompatibleWith(IllegalArgumentException.class));
    }

}