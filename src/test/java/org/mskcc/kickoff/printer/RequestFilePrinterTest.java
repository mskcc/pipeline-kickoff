package org.mskcc.kickoff.printer;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.validator.ErrorRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

public class RequestFilePrinterTest {

    private String projectId = "LLL";
    private KickoffRequest request;
    private RequestFilePrinter requestFilePrinter;
    private ErrorRepository errorRepository;

    @Before
    public void setup() {
        request = new KickoffRequest(projectId, Mockito.mock(ProcessingType.class));
        errorRepository = Mockito.mock(ErrorRepository.class);
        requestFilePrinter = new RequestFilePrinter(Mockito.mock(ObserverManager.class), errorRepository);
    }

    @Test
    public void whenPiEmailIsEmpty_shouldRetrievePIFromLabHead() {
        Map<String, String> projectInfo = ImmutableMap.<String, String>builder()
//                .put(Constants.ProjectInfo.PROJECT_MANAGER, Constants.NO_PM)
                .put(Constants.ProjectInfo.INVESTIGATOR_EMAIL, "inv@mail.com")
                .put(Constants.ProjectInfo.LAB_HEAD, "labhead")
                .put(Constants.ProjectInfo.LAB_HEAD_E_MAIL, "head@mail.com")
                .put(Constants.ProjectInfo.REQUESTOR_E_MAIL, "req@mail.com")
                .put(Constants.ProjectInfo.PI_FIRSTNAME, "PIF")
                .put(Constants.ProjectInfo.PI_LASTNAME, "PIL")
                .put(Constants.ProjectInfo.PI_EMAIL, "")
                .put(Constants.ProjectInfo.CONTACT_NAME, "con@mail.com")
                .build();

        request.setProjectInfo(projectInfo);

        Map<String, String> act = ReflectionTestUtils.invokeMethod(requestFilePrinter,
                "constructFieldValues", request);
        Assertions.assertThat(act).containsEntry("PI_E-mail", "head@mail.com");
        Assertions.assertThat(act).containsEntry("PI_Name", "labhead");
        Assertions.assertThat(act).containsEntry("PI", "head");
        Assertions.assertThat(act).containsEntry("Investigator_E-mail", "inv@mail.com");
    }

    @Test
    public void whenPiEmailIsNotEmpty_shouldRetrievePIFromPIemail() {
        Map<String, String> projectInfo = ImmutableMap.<String, String>builder()
                .put(Constants.ProjectInfo.PROJECT_MANAGER, "f l")
                .put(Constants.ProjectInfo.INVESTIGATOR_EMAIL, "inv@mail.com")
                .put(Constants.ProjectInfo.LAB_HEAD, "head")
                .put(Constants.ProjectInfo.LAB_HEAD_E_MAIL, "head@mail.com")
                .put(Constants.ProjectInfo.REQUESTOR_E_MAIL, "req@mail.com")
                .put(Constants.ProjectInfo.PI_FIRSTNAME, "PIF")
                .put(Constants.ProjectInfo.PI_LASTNAME, "PIL")
                .put(Constants.ProjectInfo.PI_EMAIL, "pi@mail.com")
                .put(Constants.ProjectInfo.CONTACT_NAME, "con@mail.com")
                .build();

        request.setProjectInfo(projectInfo);

        Map<String, String> act = ReflectionTestUtils.invokeMethod(requestFilePrinter,
                "constructFieldValues", request);
        Assertions.assertThat(act).containsEntry("PI_E-mail", "pi@mail.com");
        Assertions.assertThat(act).containsEntry("PI_Name", "PIL, PIF");
        Assertions.assertThat(act).containsEntry("PI", "head");
        Assertions.assertThat(act).containsEntry("Investigator_E-mail", "inv@mail.com");
    }

}
