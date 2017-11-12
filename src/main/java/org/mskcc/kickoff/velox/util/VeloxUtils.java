package org.mskcc.kickoff.velox.util;

import com.velox.sapioutils.client.standalone.VeloxConnection;
import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class VeloxUtils {
    private static final Logger devLogger = Logger.getLogger(Constants.DEV_LOGGER);

    public static VeloxConnection getVeloxConnection(String connectionFilePath) {
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
            devLogger.warn(String.format("Connecting to lims using parameters: host: %s, port: %s, username: %s", host, port, username));
        } catch (IOException ioe) {
            devLogger.warn(String.format("Cannot read connection file: %s", connectionFilePath), ioe);
        }
        return new VeloxConnection(host, port, guid, username, password);
    }
}
