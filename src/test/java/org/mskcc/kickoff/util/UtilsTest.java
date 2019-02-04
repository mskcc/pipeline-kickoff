package org.mskcc.kickoff.util;

import org.assertj.core.api.Assertions;
import org.junit.Test;

public class UtilsTest {

    @Test
    public void whenBlankEmail_shouldReturnFalse() {
        Assertions.assertThat(Utils.isValidMSKemail(" ")).isFalse();
    }

    @Test
    public void whenMskccEmail_shouldReturnTrue() {
        Assertions.assertThat(Utils.isValidMSKemail("bin@mskcc.org")).isTrue();
    }

    @Test
    public void whenNotMskccEmail_shouldReturnFalse() {
        Assertions.assertThat(Utils.isValidMSKemail("bin@sys.com")).isFalse();
    }

    @Test
    public void whenPmIsBlank_thenIsCMOSideProject() {
        Assertions.assertThat(Utils.isCmoSideProject("")).isTrue();
    }

    @Test
    public void whenNOPM_thenIsCMOSideProject() {
        Assertions.assertThat(Utils.isCmoSideProject("NO PM")).isTrue();
    }

    @Test
    public void whenPMExist_thenIsNotCMOSideProject() {
        Assertions.assertThat(Utils.isCmoSideProject("kit wit")).isFalse();
    }
}
