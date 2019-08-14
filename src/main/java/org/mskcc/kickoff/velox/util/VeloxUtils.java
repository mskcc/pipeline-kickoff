package org.mskcc.kickoff.velox.util;

import com.velox.sapioutils.client.standalone.VeloxConnection;
import com.velox.sapioutils.client.standalone.VeloxConnectionException;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class VeloxUtils {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);
    private static VeloxConnection connection;

    public static VeloxConnection tryToConnect(String limsConnectionFilePath, int limsConnectionAttempts)
            throws VeloxConnectionException {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        connection = getVeloxConnection(limsConnectionFilePath);
        CountDownLatch latch = new CountDownLatch(1);

        final ScheduledFuture<?> future = executor.scheduleAtFixedRate(new Runnable() {
            int attempt = 0;

            public void run() {
                try {
                    attempt++;
                    devLogger.debug(String.format("Trying to connect to LIMS. Attempt: %d. Max attempts: %d",
                            attempt, limsConnectionAttempts));
                    boolean opened = connection.open();
                    if (opened) {
                        devLogger.debug("Aquired connection to LIMS");
                        latch.countDown();
                    }
                } catch (Exception omitted) {
                } finally {
                    if (attempt >= limsConnectionAttempts) latch.countDown();
                }
            }
        }, 0, 1, TimeUnit.SECONDS);

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new VeloxConnectionException("Connection to LIMS timed out", e);
        } finally {
            future.cancel(false);
        }

        executor.shutdown();

        if (!connection.isConnected()) {
            throw new VeloxConnectionException("Connection to LIMS timed out");
        }

        return connection;
    }

    private static VeloxConnection getVeloxConnection(String connectionFilePath) {
        String host = "";
        String username = "";
        String guid = "";
        String password = "";
        int port = 0;

        try (BufferedReader br = new BufferedReader(new FileReader(connectionFilePath))) {
            String[] connectionElements = br.readLine().split(", ");
            host = connectionElements[0].replace("Host: ", "");
            port = Integer.parseInt(connectionElements[1].replace("Port: ", ""));
            username = connectionElements[2].replace("User: ", "");
            password = connectionElements[3].replace("Password: ", "");
            guid = connectionElements[4].trim().replace("GUID: ", "");
        } catch (IOException ioe) {
            devLogger.warn(String.format("Cannot read connection file: %s", connectionFilePath), ioe);
        }
        return new VeloxConnection(host, port, guid, username, password);
    }

    public static VeloxConnection getConnection() {
        return connection;
    }

    public static void closeConnection() {
        if (connection != null && connection.isConnected()) {
            try {
                devLogger.debug("Closing LIMS connection");
                connection.close();
            } catch (Throwable e) {
                devLogger.error(e.getMessage(), e);
            }
        }
    }
}
