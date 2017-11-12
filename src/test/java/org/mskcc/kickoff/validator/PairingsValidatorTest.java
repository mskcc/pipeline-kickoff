package org.mskcc.kickoff.validator;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;

import java.util.Arrays;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class PairingsValidatorTest {
    private final PairingsValidator pairingsValidator = new PairingsValidator((t, n) -> t.getIgoId().startsWith
            ("valid") && n.getIgoId().startsWith("valid"));
    private KickoffRequest request;

    @Before
    public void setUp() throws Exception {
        request = new KickoffRequest("id", mock(NormalProcessingType.class));
    }

    @Test
    public void whenThereAreNoPairings_shouldReturnValid() throws Exception {
        KickoffRequest request = new KickoffRequest("id", mock(NormalProcessingType.class));

        boolean valid = pairingsValidator.isValid(request.getPairingInfos());

        assertThat(valid, is(true));
    }

    @Test
    public void whenThereInOneValidPairing_shouldReturnValid() throws Exception {
        //given
        request.setPairingInfos(Arrays.asList(getValidPairingInfo()));

        //when
        boolean valid = pairingsValidator.isValid(request.getPairingInfos());

        //then
        assertThat(valid, is(true));
    }

    @Test
    public void whenThereAreTwoValidPairings_shouldReturnValid() throws Exception {
        //given
        request.setPairingInfos(Arrays.asList(getValidPairingInfo(), getValidPairingInfo()));

        //when
        boolean valid = pairingsValidator.isValid(request.getPairingInfos());

        //then
        assertThat(valid, is(true));
    }

    @Test
    public void whenThereIsOneInvalidPairing_shouldReturnInvalid() throws Exception {
        //given
        request.setPairingInfos(Arrays.asList(getInvalidPairingInfo()));

        //when
        boolean valid = pairingsValidator.isValid(request.getPairingInfos());

        //then
        assertThat(valid, is(false));
    }

    @Test
    public void whenThereIsOneValidAndOneInvalidPairing_shouldReturnInvalid() throws Exception {
        //given
        request.setPairingInfos(Arrays.asList(getValidPairingInfo(), getInvalidPairingInfo()));

        //when
        boolean valid = pairingsValidator.isValid(request.getPairingInfos());

        //then
        assertThat(valid, is(false));
    }

    @Test
    public void whenThereAreMultipleValidAndOneInvalidPairing_shouldReturnInvalid() throws Exception {
        //given
        request.setPairingInfos(Arrays.asList(
                getValidPairingInfo(),
                getValidPairingInfo(),
                getValidPairingInfo(),
                getInvalidPairingInfo(),
                getValidPairingInfo()));

        //when
        boolean valid = pairingsValidator.isValid(request.getPairingInfos());

        //then
        assertThat(valid, is(false));
    }

    private PairingInfo getValidPairingInfo() {
        return new PairingInfo(new Sample("valid"), new Sample("valid"));
    }

    private PairingInfo getInvalidPairingInfo() {
        return new PairingInfo(new Sample("invalid"), new Sample("invalid"));
    }

}