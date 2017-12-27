package org.mskcc.kickoff.velox;

public class VeloxConnectionData {
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String guid;

    public VeloxConnectionData(String host, int port, String username, String password, String guid) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.guid = guid;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getGuid() {
        return guid;
    }
}
