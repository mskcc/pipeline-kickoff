package org.mskcc.kickoff.validator;

import org.junit.Before;
import org.junit.Test;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;

public class PairingInfoValidPredicateTest {
    private static int id = 0;
    private final PairingInfoValidPredicate pairingInfoValidPredicate = new PairingInfoValidPredicate(mock
            (ErrorRepository.class));

    @Before
    public void setUp() throws Exception {
        InstrumentType.mapNameToType("PITT", InstrumentType.HISEQ);
        InstrumentType.mapNameToType("JAX", InstrumentType.HISEQ);
        InstrumentType.mapNameToType("LOLA", InstrumentType.HISEQ);
        InstrumentType.mapNameToType("BRAD", InstrumentType.HISEQ);
        InstrumentType.mapNameToType("LIZ", InstrumentType.HISEQ);
        InstrumentType.mapNameToType("VIC", InstrumentType.MISEQ);
        InstrumentType.mapNameToType("JOHNSAWYERS", InstrumentType.MISEQ);
        InstrumentType.mapNameToType("MICHELLE", InstrumentType.NOVASEQ);
        InstrumentType.mapNameToType("SCOTT", InstrumentType.NEXT_SEQ);
    }

    @Test
    public void whenTumorAndNormalInstrumentNameIsTheSame_shouldReturnValid() throws Exception {
        assertInstrumentsCompatibility("PITT", "PITT", true);
        assertInstrumentsCompatibility("JAX", "JAX", true);
        assertInstrumentsCompatibility("SCOTT", "SCOTT", true);
        assertInstrumentsCompatibility("PITT", "LIZ", true);
        assertInstrumentsCompatibility("PITT", "VIC", true);
        assertInstrumentsCompatibility("VIC", "VIC", true);
        assertInstrumentsCompatibility("VIC", "LIZ", true);
        assertInstrumentsCompatibility("JOHNSAWYERS", "VIC", true);
        assertInstrumentsCompatibility("SCOTT", "SCOTT", true);
        assertInstrumentsCompatibility("MICHELLE", "MICHELLE", true);

        assertInstrumentsCompatibility("JAX", "MICHELLE", false);
        assertInstrumentsCompatibility("MICHELLE", "VIC", false);
        assertInstrumentsCompatibility("SCOTT", "VIC", false);
        assertInstrumentsCompatibility("SCOTT", "MICHELLE", false);
        assertInstrumentsCompatibility("MICHELLE", "SCOTT", false);
    }

    private void assertInstrumentsCompatibility(String tumorSequencer, String normalSequencer, boolean expected) {
        Sample tumor = getSampleWithSeqName(tumorSequencer);
        Sample normal = getSampleWithSeqName(normalSequencer);

        boolean valid = pairingInfoValidPredicate.test(tumor, normal);

        assertThat(valid, is(expected));
    }

    private Sample getSampleWithSeqName(String seqName) {
        Sample sample = new Sample(String.valueOf(id++));
        sample.setSeqName(seqName);

        return sample;
    }

}