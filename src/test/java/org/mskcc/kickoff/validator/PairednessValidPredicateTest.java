package org.mskcc.kickoff.validator;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.mskcc.domain.Pairedness;

import java.util.Collections;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public class PairednessValidPredicateTest {
    private final PairednessValidPredicate pairednessValidPredicate = new PairednessValidPredicate();

    @Test
    public void whenThereIsNoPairednessSet_shouldReturnInvalid() throws Exception {
        assertPairedness(Collections.emptySet(), false);
        assertPairedness(Sets.newHashSet(Pairedness.PE), true);
        assertPairedness(Sets.newHashSet(Pairedness.PE, Pairedness.PE), true);
        assertPairedness(Sets.newHashSet(Pairedness.SE), true);
        assertPairedness(Sets.newHashSet(Pairedness.SE, Pairedness.SE), true);
        assertPairedness(Sets.newHashSet(Pairedness.SE, Pairedness.PE), false);
        assertPairedness(Sets.newHashSet(Pairedness.SE, Pairedness.PE, Pairedness.PE), false);
    }

    private void assertPairedness(Set<Pairedness> pairednesses, boolean value) {
        boolean valid = pairednessValidPredicate.test(pairednesses);
        assertThat(valid, is(value));
    }
}