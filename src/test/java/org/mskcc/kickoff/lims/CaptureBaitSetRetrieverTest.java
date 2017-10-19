package org.mskcc.kickoff.lims;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CaptureBaitSetRetrieverTest {
    private final CaptureBaitSetRetriever captureBaitSetRetriever = new CaptureBaitSetRetriever();

    @Test
    public void whenMappingIsEmpty_shouldReturnBaitSet() throws Exception {
        String baitSet = "ffdf";
        Map<String, String> mapping = new HashMap<>();
        String captureBaitSet = captureBaitSetRetriever.retrieve(baitSet, mapping, "124432");

        assertThat(captureBaitSet, is(baitSet));
    }

    @Test
    public void whenMappingForBaitSetExists_shouldReturnMappedDesignFile() throws Exception {
        String baitSet = "ffdf";
        String designFile = "fewfef_fefe_fef";
        Map<String, String> mapping = new HashMap<>();
        mapping.put(baitSet, designFile);
        String captureBaitSet = captureBaitSetRetriever.retrieve(baitSet, mapping, "124432");

        assertThat(captureBaitSet, is(designFile));
    }

    @Test
    public void whenMappingForBaitSetDoesntExists_shouldReturnBaiSet() throws Exception {
        String baitSet = "ffdf";
        String designFile = "fewfef_fefe_fef";
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cos_zupelnie_innego", designFile);
        String captureBaitSet = captureBaitSetRetriever.retrieve(baitSet, mapping, "124432");

        assertThat(captureBaitSet, is(baitSet));
    }

}