package org.mskcc.kickoff.generator;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.kickoff.domain.KickoffRequest;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PairingsResolverTest {
    private PairingsResolver pairingsResolver;
    private PairingInfoRetriever pairingInfoRetriever = mock(PairingInfoRetriever.class);
    private SmartPairingRetriever smartPairingRetriever = mock(SmartPairingRetriever.class);
    private KickoffRequest request = mock(KickoffRequest.class);

    @Before
    public void setUp() throws Exception {
         pairingsResolver = new PairingsResolver(pairingInfoRetriever, smartPairingRetriever);
    }

    @Test
    public void whenPatientsListAndPairingInfoListAreEmpty_shouldReturnEmptyPairings() throws Exception {
        Map<String, String> pairings = pairingsResolver.resolve(request);

        assertThat(pairings.size(), is(0));
    }

    @Test
    public void whenPatientsListHasOnePairingAndPairingInfoIsEmpty_shouldReturnThisOnePairing() throws Exception {
        //given
        Map<String, String> smartPairings = new HashMap<>();
        String tum1 = "some tumor";
        String norm1 = "another normal";
        smartPairings.put(tum1, norm1);
        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(1));
        assertThat(pairings.containsKey(tum1), is(true));
        assertThat(pairings.get(tum1), is(norm1));
    }

    @Test
    public void whenPatientsListHasMultiplePairingsAndPairingInfoIsEmpty_shouldReturnPairingsWithAllSmartPairings() throws Exception {
        //given
        Map<String, String> smartPairings = new HashMap<>();
        String tum1 = "some tumor";
        String norm1 = "another normal";
        smartPairings.put(tum1, norm1);

        String tum2 = "blabal";
        String norm2 = "acoto";
        smartPairings.put(tum2, norm2);

        String tum3 = "ktoto";
        String norm3 = "haha";
        smartPairings.put(tum3, norm3);

        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(3));
        assertThat(pairings.containsKey(tum1), is(true));
        assertThat(pairings.get(tum1), is(norm1));

        assertThat(pairings.containsKey(tum2), is(true));
        assertThat(pairings.get(tum2), is(norm2));

        assertThat(pairings.containsKey(tum3), is(true));
        assertThat(pairings.get(tum3), is(norm3));
    }

    @Test
    public void whenPatientsListIsEmptyAndPairingInfoHasOnePairing_shouldReturnPairingsWithThisOnePairing() throws Exception {
        //given
        Map<String, String> pairingInfos = new HashMap<>();
        String tum1 = "tum1";
        String norm1 = "norm1";
        pairingInfos.put(tum1, norm1);
        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(1));
        assertThat(pairings.containsKey(tum1), is(true));
        assertThat(pairings.get(tum1), is(norm1));
    }

    @Test
    public void whenPatientsListIsEmptyAndPairingInfoHasMultiplePairings_shouldReturnPairingsWithAllThosePairings() throws Exception {
        //given
        Map<String, String> pairingInfos = new HashMap<>();
        String tum1 = "tum1";
        String norm1 = "norm1";
        pairingInfos.put(tum1, norm1);

        String tum2 = "blabal2";
        String norm2 = "acoto";
        pairingInfos.put(tum2, norm2);

        String tum3 = "ktoto";
        String norm3 = "haha";
        pairingInfos.put(tum3, norm3);

        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(3));
        assertThat(pairings.containsKey(tum1), is(true));
        assertThat(pairings.get(tum1), is(norm1));

        assertThat(pairings.containsKey(tum2), is(true));
        assertThat(pairings.get(tum2), is(norm2));

        assertThat(pairings.containsKey(tum3), is(true));
        assertThat(pairings.get(tum3), is(norm3));
    }

    @Test
    public void whenPatientsListHasOnePairingAndPairingInfoHasDifferentOnePairing_shouldReturnPairingsWithBothPairings() throws Exception {
        //given
        Map<String, String> pairingInfos = new HashMap<>();
        String tum1 = "tum1";
        String norm1 = "norm1";
        pairingInfos.put(tum1, norm1);
        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        Map<String, String> smartPairings = new HashMap<>();
        String smartTum1 = "some tumor";
        String smartNorm1 = "another normal";
        smartPairings.put(smartTum1, smartNorm1);
        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(2));
        assertThat(pairings.containsKey(tum1), is(true));
        assertThat(pairings.get(tum1), is(norm1));

        assertThat(pairings.containsKey(smartTum1), is(true));
        assertThat(pairings.get(smartTum1), is(smartNorm1));
    }

    @Test
    public void whenPatientsListHasOnePairingAndPairingInfoHasSameOnePairing_shouldReturnOnePairingWithOverriddenValueFromPairingInfo() throws Exception {
        //given
        Map<String, String> smartPairings = new HashMap<>();
        String tumor = "tum1";
        String smartNorm1 = "another normal";
        smartPairings.put(tumor, smartNorm1);
        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        Map<String, String> pairingInfos = new HashMap<>();
        String pairingInfoNormal = "norm1";
        pairingInfos.put(tumor, pairingInfoNormal);
        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(1));
        assertThat(pairings.containsKey(tumor), is(true));
        assertThat(pairings.get(tumor), is(pairingInfoNormal));
    }

    @Test
    public void whenPatientsListHasOnePairingAndPairingInfoHasSameOnePairingWithEmptyNormal_shouldReturnOnePairingWithValueFromSmartPairing() throws Exception {
        //given
        Map<String, String> smartPairings = new HashMap<>();
        String tumor = "tum1";
        String smartNormal = "some normal normal";
        smartPairings.put(tumor, smartNormal);
        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        Map<String, String> pairingInfos = new HashMap<>();
        String emptyNormal = "";
        pairingInfos.put(tumor, emptyNormal);
        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(1));
        assertThat(pairings.containsKey(tumor), is(true));
        assertThat(pairings.get(tumor), is(smartNormal));
    }

    @Test
    public void whenPatientsListHasOnePairingAndPairingInfoHasSameOnePairingWithNotAvailableValueSet_shouldReturnOnePairingWithOverriddenValueFromPairingInfo() throws Exception {
        //given
        Map<String, String> smartPairings = new HashMap<>();
        String tumor = "tum1";
        String smartNorm1 = "another normal";
        smartPairings.put(tumor, smartNorm1);
        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        Map<String, String> pairingInfos = new HashMap<>();
        String pairingInfoNormal = "na";
        pairingInfos.put(tumor, pairingInfoNormal);
        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(1));
        assertThat(pairings.containsKey(tumor), is(true));
        assertThat(pairings.get(tumor), is(pairingInfoNormal));
    }

    @Test
    public void whenPatientsListHasMultiplePairingsAndPairingInfoHasSomeSamePairings_shouldReturnAllPairingsWithOverriddenValuesFromPairingInfo() throws Exception {
        //given
        Map<String, String> smartPairings = new HashMap<>();
        String tumor = "tum1";
        String smartNorm1 = "another normal";
        smartPairings.put(tumor, smartNorm1);

        String tumor2 = "tum2";
        String smartNorm2 = "another normal2";
        smartPairings.put(tumor, smartNorm2);

        String tumor3 = "tum3";
        String smartNorm3 = "another normal3";
        smartPairings.put(tumor3, smartNorm3);
        when(smartPairingRetriever.retrieve(request)).thenReturn(smartPairings);

        Map<String, String> pairingInfos = new HashMap<>();
        String pairingInfoNormal = "norm1";
        pairingInfos.put(tumor, pairingInfoNormal);

        String pairingInfoNormal2 = "norm2";
        pairingInfos.put(tumor2, pairingInfoNormal2);

        String pairingInfoTumor = "pairing info tumor";
        String pairingInfoNormal3 = "norm3";
        pairingInfos.put(pairingInfoTumor, pairingInfoNormal3);
        when(pairingInfoRetriever.retrieve(any(), any())).thenReturn(pairingInfos);

        //when
        Map<String, String> pairings = pairingsResolver.resolve(request);

        //then
        assertThat(pairings.size(), is(4));

        assertThat(pairings.containsKey(tumor), is(true));
        assertThat(pairings.get(tumor), is(pairingInfoNormal));

        assertThat(pairings.containsKey(tumor2), is(true));
        assertThat(pairings.get(tumor2), is(pairingInfoNormal2));

        assertThat(pairings.containsKey(tumor3), is(true));
        assertThat(pairings.get(tumor3), is(smartNorm3));

        assertThat(pairings.containsKey(pairingInfoTumor), is(true));
        assertThat(pairings.get(pairingInfoTumor), is(pairingInfoNormal3));
    }
}