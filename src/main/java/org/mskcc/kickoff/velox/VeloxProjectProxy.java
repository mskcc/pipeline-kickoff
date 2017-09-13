package org.mskcc.kickoff.velox;

import com.velox.api.datarecord.DataRecordManager;
import com.velox.api.user.User;
import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxStandalone;
import com.velox.sapioutils.client.standalone.VeloxStandaloneException;
import com.velox.sapioutils.client.standalone.VeloxTask;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.archive.ProjectFilesArchiver;
import org.mskcc.kickoff.converter.ProjectInfoConverter;
import org.mskcc.kickoff.domain.KickoffRequest;
import org.mskcc.kickoff.process.NormalProcessingType;
import org.mskcc.kickoff.proxy.RequestProxy;
import org.mskcc.kickoff.retriever.RequestNotFoundException;
import org.mskcc.kickoff.retriever.RequestsRetriever;
import org.mskcc.kickoff.util.Constants;
import org.mskcc.kickoff.velox.util.VeloxUtils;

public class VeloxProjectProxy implements RequestProxy {
    private static final Logger DEV_LOGGER = Logger.getLogger(Constants.DEV_LOGGER);
    private static final Logger PM_LOGGER = Logger.getLogger(Constants.PM_LOGGER);
    private final String limsConnectionFilePath;
    private final ProjectFilesArchiver projectFilesArchiver;
    private final RequestsRetrieverFactory requestsRetrieverFactory;
    private DataRecordManager dataRecordManager;
    private User user;
    private RequestsRetriever requestsRetriever;

    public VeloxProjectProxy(String limsConnectionFilePath, ProjectFilesArchiver projectFilesArchiver, RequestsRetrieverFactory requestsRetrieverFactory) {
        this.limsConnectionFilePath = limsConnectionFilePath;
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
        VeloxConnection connection = VeloxUtils.getVeloxConnection(limsConnectionFilePath);
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
            requestsRetriever = requestsRetrieverFactory.getRequestsRetriever(user, dataRecordManager, projectId);
            KickoffRequest kickoffRequest = requestsRetriever.retrieve(projectId, new NormalProcessingType(projectFilesArchiver));
            return kickoffRequest;
        } catch (RequestNotFoundException e) {
            String message = String.format("No matching requests for request id: %s", projectId);
            PM_LOGGER.info(message);
            throw e;
        } catch (ProjectInfoConverter.PrimaryRequestNotSetException | ProjectInfoConverter.PrimaryRequestNotPartOfSampleSetException | ProjectInfoConverter.PropertyInPrimaryRequestNotSetException e) {
            PM_LOGGER.error(e.getMessage());
            throw e;
        } catch (Exception e) {
            throw new ProjectRetrievalException(String.format("Error while retrieving information about project: %s", projectId), e);
        } finally {
            closeConnection(connection);
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
