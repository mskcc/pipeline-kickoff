package org.mskcc.kickoff.retriever;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;

public class DummyNimblegenResolver implements NimblegenResolver {
    @Override
    public DataRecord resolve(DataRecordManager drm, DataRecord rec, User apiUser, boolean isPoolNormal) {
        return null;
    }

    @Override
    public boolean shouldRetrieve() {
        return false;
    }
}
