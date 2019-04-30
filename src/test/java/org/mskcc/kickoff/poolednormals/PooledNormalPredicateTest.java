package org.mskcc.kickoff.poolednormals;

import org.junit.Test;
import org.mskcc.kickoff.util.Constants;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class PooledNormalPredicateTest {
    private PooledNormalPredicate pooledNormalPredicate = new PooledNormalPredicate();

    @Test
    public void whenSampleId_shouldCheckIfItsPooledNormal() throws Exception {
        assertIsPooledNormal("CTRL-", "", true);
        assertIsPooledNormal("CTRL-1234", "", true);
        assertIsPooledNormal("CTRL-54G", "CTRL-123", true);
        assertIsPooledNormal("CTRL", "POOLEDNORMAL", false);

        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-", "whateva", true);
        assertIsPooledNormal("something", Constants.FFPEPOOLEDNORMAL + "-132", true);
        assertIsPooledNormal("CTRL-543", Constants.FFPEPOOLEDNORMAL + "-1", true);
        assertIsPooledNormal("", Constants.FFPEPOOLEDNORMAL + "-679987", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-67AB", "", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL, "tezNieTo", false);

        assertIsPooledNormal("153421_A_1", Constants.FROZENPOOLEDNORMAL + "-", true);
        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL + "-5436", "", true);
        assertIsPooledNormal("CTRL-56", Constants.FROZENPOOLEDNORMAL + "-A6453", true);
        assertIsPooledNormal("", Constants.FROZENPOOLEDNORMAL + "-1", true);
        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL, "CTRL", false);
    }

    private void assertIsPooledNormal(String sampleId, String otherSampleId, boolean expected) {
        boolean isPooledNormal = pooledNormalPredicate.test(sampleId, otherSampleId);
        assertThat(isPooledNormal, is(expected));
    }

}