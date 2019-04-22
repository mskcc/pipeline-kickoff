package org.mskcc.kickoff.poolednormals;

import org.junit.Test;
import org.mskcc.kickoff.util.Constants;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class PooledNormalPredicateTest {
    private PooledNormalPredicate pooledNormalPredicate = new PooledNormalPredicate();

    @Test
    public void whenSampleId_shouldCheckIfItsPooledNormal() throws Exception {
        assertIsPooledNormal("CTRL-", true);
        assertIsPooledNormal("CTRL-1234", true);
        assertIsPooledNormal("CTRL-54G", true);
        assertIsPooledNormal("CTRL", false);

        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-132", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-1", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-679987", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL + "-67AB", true);
        assertIsPooledNormal(Constants.FFPEPOOLEDNORMAL, false);

        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL + "-", true);
        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL + "-5436", true);
        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL + "-A6453", true);
        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL + "-1", true);
        assertIsPooledNormal(Constants.FROZENPOOLEDNORMAL, false);
    }

    private void assertIsPooledNormal(String sampleId, boolean expected) {
        boolean isPooledNormal = pooledNormalPredicate.test(sampleId);
        assertThat(isPooledNormal, is(expected));
    }

}