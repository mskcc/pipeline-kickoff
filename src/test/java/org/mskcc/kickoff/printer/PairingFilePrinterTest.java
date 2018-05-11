package org.mskcc.kickoff.printer;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.RequestType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.pairing.PairingsResolver;
import org.mskcc.kickoff.printer.observer.ObserverManager;
import org.mskcc.kickoff.process.ProcessingType;
import org.mskcc.kickoff.util.Constants;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PairingFilePrinterTest {
    private final String tum1 = "tum1";
    private final String norm1 = "norm1";
    private final String ffpePooledNormal = "FFPEPOOLEDNORMAL";
    private final String frozenPooledNormal = "FROZENPOOLEDNORMAL";
    private ProcessingType processingType;
    private KickoffRequest request;

    private PairingsResolver pairingsResolver = mock(PairingsResolver.class);
    private PairingFilePrinter pairingFilePrinter = new PairingFilePrinter(pairingsResolver, mock(ObserverManager
            .class));

    @Before
    public void setUp() throws Exception {
        processingType = mock(ProcessingType.class);
        request = new KickoffRequest("id", processingType);
    }

    @Test
    public void whenOneTumorOneNormal_shouldPairingSamplesContainBoth() throws Exception {
        Map<String, String> pairings = new HashMap<>();
        pairings.put(norm1, tum1);

        Set<String> expectedPairings = new HashSet<>(pairings.keySet());
        expectedPairings.addAll(pairings.values());

        assertPairingSamples(pairings, expectedPairings);
    }

    @Test
    public void whenOneUnmatchedTumor_shouldPairingSamplesContainOneTumor() throws Exception {
        Map<String, String> pairings = new HashMap<>();
        pairings.put(Constants.NA_LOWER_CASE, tum1);

        Set<String> expectedPairings = new HashSet<>();
        expectedPairings.add(tum1);

        assertPairingSamples(pairings, expectedPairings);
    }

    @Test
    public void whenOneUnmatchedNormal_shouldPairingSamplesContainOneNormal() throws Exception {
        Map<String, String> pairings = new HashMap<>();
        pairings.put(norm1, Constants.NA_LOWER_CASE);

        Set<String> expectedPairings = new HashSet<>();
        expectedPairings.add(norm1);

        assertPairingSamples(pairings, expectedPairings);
    }

    @Test
    public void whenOneUnmatchedPooledNormal_shouldPairingSamplesContainNothing() throws Exception {
        Map<String, String> pairings = new HashMap<>();
        pairings.put(ffpePooledNormal, Constants.NA_LOWER_CASE);

        Set<String> expectedPairings = new HashSet<>();
        expectedPairings.add(ffpePooledNormal);

        assertPairingSamples(pairings, expectedPairings);
    }

    @Test
    public void whenExomeRequestAndOneUnmatchedPooledNormal_shouldPairingSamplesContainNothing() throws Exception {
        request.setRequestType(RequestType.EXOME);
        Map<String, String> pairings = new HashMap<>();
        pairings.put(ffpePooledNormal, Constants.NA_LOWER_CASE);

        Set<String> expectedPairings = new HashSet<>();
        expectedPairings.add(ffpePooledNormal);

        assertPairingSamples(pairings, expectedPairings);
    }

    private void assertPairingSamples(Map<String, String> pairings, Set<String> expectedPairings) {
        //given


        Map<String, Sample> validNormals = new HashMap<>();
        validNormals.put(norm1, new Sample(norm1));
        validNormals.put(ffpePooledNormal, new Sample(ffpePooledNormal));
        validNormals.put(frozenPooledNormal, new Sample(frozenPooledNormal));
        when(processingType.getAllValidSamples(any(), any())).thenReturn(validNormals);

        Sample normSample = new Sample(norm1);
        normSample.setCorrectedCmoId(norm1);
        normSample.setIsTumor(false);
        request.putSampleIfAbsent(normSample);

        when(pairingsResolver.resolve(request)).thenReturn(pairings);

        //when
        pairingFilePrinter.print(request);

        //then
        assertThat(request.getPairingSampleIds()).containsAll(expectedPairings);
    }

}