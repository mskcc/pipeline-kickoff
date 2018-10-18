package org.mskcc.kickoff.validator;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mskcc.domain.Recipe;
import org.mskcc.kickoff.validator.SampleSetBaitSetCompatibilityPredicate.Pair;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mskcc.domain.Recipe.*;

@RunWith(Parameterized.class)
public class SampleSetBaitSetCompatibilityPredicateTest {
    private static SampleSetBaitSetCompatibilityPredicate compatibilityPredicate;
    private final String baitSet1;
    private final String baitSet2;
    private final boolean expected;

    public SampleSetBaitSetCompatibilityPredicateTest(Recipe baitSet1, Recipe baitSet2, boolean expected) {
        this.baitSet1 = baitSet1.getValue();
        this.baitSet2 = baitSet2.getValue();
        this.expected = expected;
    }

    @BeforeClass
    public static void init() {
        List<Pair<String>> compatibility = Arrays.asList(
                new Pair<>(IMPACT_341.getValue(), IMPACT_410.getValue()),
                new Pair<>(IMPACT_341.getValue(), IMPACT_468.getValue()),
                new Pair<>(IMPACT_410.getValue(), IMPACT_468.getValue())
        );

        compatibilityPredicate = new SampleSetBaitSetCompatibilityPredicate(compatibility);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> compatibility() {
        return Arrays.asList(new Object[][]{
                {IMPACT_341, IMPACT_341, true},
                {IMPACT_341, IMPACT_410, true},
                {IMPACT_341, IMPACT_468, true},
                {IMPACT_341, IMPACT_410_PLUS, false},
                {IMPACT_341, WHOLE_EXOME_SEQUENCING, false},

                {IMPACT_410, IMPACT_410, true},
                {IMPACT_410, IMPACT_410_PLUS, false},
                {IMPACT_410, IMPACT_468, true},
                {IMPACT_410, IMPACT_341, true},
                {IMPACT_410, RNA_SEQ, false},

                {IMPACT_468, IMPACT_468, true},
                {IMPACT_468, IMPACT_341, true},
                {IMPACT_468, IMPACT_410, true},
                {IMPACT_468, IMPACT_410_PLUS, false},
                {IMPACT_468, RNA_SEQ_POLY_A, false},

                {RNA_SEQ_POLY_A, RNA_SEQ_POLY_A, true},
                {RNA_SEQ, RNA_SEQ, true},
                {RNA_SEQ_POLY_A, RNA_SEQ, false},
                {RNA_SEQ_RIBO_DEPLETE, RNA_SEQ, false},
                {Recipe.WHOLE_EXOME_SEQUENCING, RNA_SEQ, false},
        });
    }

    @Test
    public void whenBaitSetPairIsInCompatibilityList_shouldReturnTrue() throws Exception {
        //when
        boolean isCompatible = compatibilityPredicate.test(baitSet1, baitSet2);

        //then
        assertThat(isCompatible, is(expected));
    }

}