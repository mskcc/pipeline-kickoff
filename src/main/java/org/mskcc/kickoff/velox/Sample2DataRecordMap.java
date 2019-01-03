package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class Sample2DataRecordMap extends HashMap<String, DataRecord> {
    public Sample2DataRecordMap() {
        super();
    }

    @Override
    public DataRecord get(Object key) {
        if (!super.containsKey(key))
            throw new RuntimeException(String.format("Sample %s doesn't exist in Sample2DataRecord map", key));

        return super.get(key);
    }
}
