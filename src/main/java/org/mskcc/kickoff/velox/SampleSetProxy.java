package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.IoError;
import com.velox.api.datarecord.NotFound;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.domain.KickoffExternalSample;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.ProcessingType;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

public interface SampleSetProxy {
    String getRecipe() throws Exception;

    String getBaitVersion() throws Exception;

    String getPrimaryRequestId() throws Exception;

    Collection<KickoffRequest> getRequests(ProcessingType processingType) throws Exception;

    Collection<Sample> getIgoSamples() throws Exception;

    List<KickoffExternalSample> getExternalSamples() throws IoError, RemoteException, NotFound;
}
