package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecord;
import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import org.apache.log4j.Logger;
import org.mskcc.domain.PairingInfo;
import org.mskcc.domain.SampleSet;
import org.mskcc.domain.instrument.InstrumentType;
import org.mskcc.domain.sample.Sample;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.converter.SampleSetProjectInfoConverter;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.retriever.RequestNotFoundException;
import org.mskcc.kickoff.retriever.RequestsRetriever;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.velox.util.VeloxUtils;
import org.mskcc.util.VeloxConstants;

import java.util.List;

public class VeloxProjectProxy implements RequestProxy {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private final ProjectFilesArchiver projectFilesArchiver;
    private final RequestsRetrieverFactory requestsRetrieverFactory;
    private final VeloxConnectionData veloxConnectionData;
    private DataRecordManager dataRecordManager;
    private User user;
    private RequestsRetriever requestsRetriever;

    public VeloxProjectProxy(VeloxConnectionData veloxConnectionData, ProjectFilesArchiver projectFilesArchiver,
                             RequestsRetrieverFactory requestsRetrieverFactory) {
        this.veloxConnectionData = veloxConnectionData;
        this.projectFilesArchiver = projectFilesArchiver;
        this.requestsRetrieverFactory = requestsRetrieverFactory;
    }

    @Override
    public KickoffRequest getRequest(String projectId) {
        VeloxConnection connection = tryToConnectToLims();

        try {
            KickoffRequest request = VeloxStandalone.run(connection, new VeloxTask<KickoffRequest>() {
                @Override
                public KickoffRequest performTask() throws VeloxStandaloneException {
                    return retrieveRequest(projectId, connection);
                }
            });

            return request;
        } catch (VeloxStandaloneException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private VeloxConnection tryToConnectToLims() {
        VeloxConnection connection;

        try {
            connection = initVeloxConnection();
        } catch (Exception e) {
            PM_LOGGER.warn("Cannot connect to LIMS");
            throw new RuntimeException(e);
        }
        return connection;
    }

    private VeloxConnection initVeloxConnection() throws Exception {
        VeloxConnection connection = VeloxUtils.getVeloxConnection(veloxConnectionData);
        addShutdownHook(connection);
        connection.open();

        if (connection.isConnected()) {
            user = connection.getUser();
            dataRecordManager = connection.getDataRecordManager();
            return connection;
        }

        throw new RuntimeException("Error while trying to connect to LIMS");
    }

    private void addShutdownHook(VeloxConnection connection) {
        MySafeShutdown sh = new MySafeShutdown(connection);
        Runtime.getRuntime().addShutdownHook(sh);
    }

    private void closeConnection(VeloxConnection connection) {
        if (connection.isConnected()) {
            try {
                connection.close();
            } catch (Throwable e) {
                DEV_LOGGER.error("Cannot close LIMS connection", e);
            }
        }
    }

    private KickoffRequest retrieveRequest(String projectId, VeloxConnection connection) {
        try {
            retrieveInstruments();
            requestsRetriever = requestsRetrieverFactory.getRequestsRetriever(user, dataRecordManager, projectId);
            KickoffRequest kickoffRequest = requestsRetriever.retrieve(projectId, new NormalProcessingType
                    (projectFilesArchiver));
            resolvePairings(kickoffRequest);

            return kickoffRequest;
        } catch (RequestNotFoundException e) {
            String message = String.format("No matching requests for request id: %s", projectId);
            PM_LOGGER.info(message);
            throw e;
        } catch (SampleSetProjectInfoConverter.PrimaryRequestNotSetException | SampleSet
                .PrimaryRequestNotPartOfSampleSetException | SampleSetProjectInfoConverter
                .PropertyInPrimaryRequestNotSetException e) {
            PM_LOGGER.error(e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new ProjectRetrievalException(String.format("Error while retrieving information about project: %s",
                    projectId), e);
        } finally {
            closeConnection(connection);
        }
    }

    private void retrieveInstruments() throws Exception {
        List<DataRecord> instruments = dataRecordManager.queryDataRecords(VeloxConstants.INSTRUMENT, null, user);

        for (DataRecord instrument : instruments) {
            String instrumentName = instrument.getStringVal(VeloxConstants.INSTRUMENT_NAME, user);
            String instrumentSerialNumber = instrument.getStringVal("SerialNumber", user);
            String instrumentType = instrument.getPickListVal(VeloxConstants.INSTRUMENT_TYPE, user);

            try {
                InstrumentType.mapNameToType(instrumentName, InstrumentType.fromString(instrumentType));
                InstrumentType.mapNameToType(instrumentSerialNumber, InstrumentType.fromString(instrumentType));
            } catch (Exception e) {
                String message = String.format("Skipping instrument type: %s as it's not supported (is invalid or " +
                        "outdated).", instrumentType);
                DEV_LOGGER.info(message);
                PM_LOGGER.info(message);
            }
        }
    }

    private void resolvePairings(KickoffRequest kickoffRequest) {
        if (!kickoffRequest.isPairingError()) {
            for (PairingInfo pairingInfo : kickoffRequest.getPairingInfos()) {
                Sample normalSample = pairingInfo.getNormal();
                Sample tumorSample = pairingInfo.getTumor();

                tumorSample.setPairing(normalSample);
                normalSample.setPairing(tumorSample);
            }
        }
    }

    public class MySafeShutdown extends Thread {
        private VeloxConnection connection;

        public MySafeShutdown(VeloxConnection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            closeConnection(connection);
        }
    }
}
