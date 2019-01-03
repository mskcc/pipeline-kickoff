package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import org.mskcc.domain.sample.Sample;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class Sample2DataRecordMap extends HashMap<Sample, DataRecord> {
}
