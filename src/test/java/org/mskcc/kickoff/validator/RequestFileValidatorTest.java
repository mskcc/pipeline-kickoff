package org.mskcc.kickoff.validator;

import com.google.common.collect.ImmutableMap;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.util.Constants;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

public class RequestFileValidatorTest {

    private RequestFileValidator requestFileValidator;

    @Before
    public void setup() {
        requestFileValidator = new RequestFileValidator(Mockito.mock(ObserverManager.class));
    }

    @Test
    public void whenCmoPMandEmailIsMSKCCandAssayIsValid_shouldPassRequestFileValidatorTest() {
        Map<String, String> projectInfo = ImmutableMap.<String, String>builder()
                .put(Constants.ProjectInfo.PROJECT_MANAGER, "Fi Fi")
                .put(Constants.ProjectInfo.IGO_PROJECT_ID, "12324")
                .put("PI_E-mail", "s@mskcc.org")
                .put("Investigator_E-mail", "s@mskcc.org")
                .put(Constants.ProjectInfo.ASSAY, "WES")
                .build();
        Assertions.assertThat(requestFileValidator.test(projectInfo)).isTrue();
    }

    @Test
    public void whenCmoPMandEmailIsNotMSKCC_shouldNotPassRequestFileValidatorTest() {
        Map<String, String> projectInfo = ImmutableMap.<String, String>builder()
                .put(Constants.ProjectInfo.PROJECT_MANAGER, "Fi Fi")
                .put(Constants.ProjectInfo.IGO_PROJECT_ID, "12324")
                .put("PI_E-mail", "s@cbio.mskcc.org")
                .put("Investigator_E-mail", "s@mskcc.org")
                .put(Constants.ProjectInfo.ASSAY, "WES")
                .build();
        Assertions.assertThat(requestFileValidator.test(projectInfo)).isFalse();
    }

    @Test
    public void whenIgoPMandEmailIsNotMSKCC_shouldPassRequestFileValidatorTest() {
        Map<String, String> projectInfo = ImmutableMap.<String, String>builder()
                .put(Constants.ProjectInfo.PROJECT_MANAGER, Constants.NO_PM)
                .put(Constants.ProjectInfo.IGO_PROJECT_ID, "12324")
                .put("PI_E-mail", "s@cbio.mskcc.org")
                .put("Investigator_E-mail", "s@gmail.com")
                .put(Constants.ProjectInfo.ASSAY, "WES")
                .build();
        Assertions.assertThat(requestFileValidator.test(projectInfo)).isTrue();
    }

    @Test
    public void whenIgoPMandEmailIsNotMSKCCAssayNotValid_shouldNotPassRequestFileValidatorTest() {
        Map<String, String> projectInfo = ImmutableMap.<String, String>builder()
                .put(Constants.ProjectInfo.PROJECT_MANAGER, Constants.NO_PM)
                .put(Constants.ProjectInfo.IGO_PROJECT_ID, "12324")
                .put("PI_E-mail", "s@cbio.mskcc.org")
                .put("Investigator_E-mail", "s@gmail.com")
                .put(Constants.ProjectInfo.ASSAY, Constants.NoKAPACaptureProtocol1)
                .build();
        Assertions.assertThat(requestFileValidator.test(projectInfo)).isFalse();
    }

    @Test
    public void whenCmoPMandEmailIsMSKCC_shouldPassPredicate() {
        boolean isCmoSide = false;
        String email = "hey@mskcc.org";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateEmail", isCmoSide, email);
        Assertions.assertThat(act).isTrue();
    }

    @Test
    public void whenCmoPMandEmailIsNotMSKCC_shouldNotPassPredicate() {
        boolean isCmoSide = false;
        String email = "hey@mskcc.com";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateEmail", isCmoSide, email);
        Assertions.assertThat(act).isFalse();
    }

    @Test
    public void whenIgoPMandEmailIsNotValid_shouldNotPassPredicate() {
        boolean isCmoSide = true;
        String email = "heymskcc.com";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateEmail", isCmoSide, email);
        Assertions.assertThat(act).isFalse();
    }

    @Test
    public void whenIgoPMandEmailIsNotMSKCC_shouldPassPredicate() {
        boolean isCmoSide = true;
        String email = "hey@mskcc.com";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateEmail", isCmoSide, email);
        Assertions.assertThat(act).isTrue();
    }

    @Test
    public void whenAssayIsWES_shouldPassPredicate() {
        String assay = "WES";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateAssay", assay);
        Assertions.assertThat(act).isTrue();
    }

    @Test
    public void whenAssayIsEmpty_shouldNotPassPredicate() {
        String assay = "";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateAssay", assay);
        Assertions.assertThat(act).isFalse();
    }

    @Test
    public void whenAssayIsNoKAPA1_shouldNotPassPredicate() {
        String assay = Constants.NoKAPACaptureProtocol1;
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateAssay", assay);
        Assertions.assertThat(act).isFalse();
    }

    @Test
    public void whenAssayIsNoKAPA2_shouldNotPassPredicate() {
        String assay = Constants.NoKAPACaptureProtocol2;
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateAssay", assay);
        Assertions.assertThat(act).isFalse();
    }

    @Test
    public void whenAssayIsNA_PredicateShallReturnFalse() {
        String assay = "";
        boolean act = ReflectionTestUtils.invokeMethod(requestFileValidator,
                "validateAssay", assay);
        Assertions.assertThat(act).isFalse();
    }
}
