package org.mskcc.kickoff.lims;

import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mskcc.kickoff.util.Constants;
import java.util.Optional;

public class ProjectInfoRetrieverTest {

    private DataRecordManager dataRecordManager;
    private User apiUser;
    private ProjectInfoRetriever projectInfoRetriever;

    @Before
    public void setUp() {
        dataRecordManager = Mockito.mock(DataRecordManager.class);
        apiUser = Mockito.mock(User.class);
        projectInfoRetriever = Mockito.spy(new ProjectInfoRetriever());
    }

    @Test
    public void whenProjectManagerEmailExist_shouldReturnEmailAddress() throws Exception {
        // given
        String projectManagerFullName = "John Doe", email = "a@b.c";
        Mockito.doReturn(Optional.of(email)).when(projectInfoRetriever).queryDatabaseForProjectManagerEmail(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());

        // when
        String actualEmail = projectInfoRetriever.getProjectManagerEmail(dataRecordManager, apiUser, projectManagerFullName);

        // then
        Assertions.assertThat(actualEmail).isEqualTo(email);
    }

    @Test
    public void whenProjectManagerHasMiddleNameAndEmailExist_shouldReturnEmailAddress() throws Exception {
        // given
        String projectManagerFullName = "John S. Doe", email = "a@b.c";
        Mockito.doReturn(Optional.of(email)).when(projectInfoRetriever).queryDatabaseForProjectManagerEmail(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());

        // when
        String actualEmail = projectInfoRetriever.getProjectManagerEmail(dataRecordManager, apiUser, projectManagerFullName);

        // then
        Assertions.assertThat(actualEmail).isEqualTo(email);
    }

    @Test
    public void whenProjectManagerEmailNotExist_shouldReturnNA() throws Exception {
        // given
        String projectManagerFullName = "John Doe";
        Mockito.doReturn(Optional.empty()).when(projectInfoRetriever).queryDatabaseForProjectManagerEmail(Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.anyString());

        // when
        String actualEmail = projectInfoRetriever.getProjectManagerEmail(dataRecordManager, apiUser, projectManagerFullName);

        // then
        Assertions.assertThat(actualEmail).isEqualTo(Constants.NA);
    }
}
