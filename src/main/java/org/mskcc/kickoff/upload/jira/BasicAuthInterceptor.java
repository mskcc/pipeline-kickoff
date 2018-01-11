package org.mskcc.kickoff.upload.jira;

import org.apache.commons.codec.binary.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

public class BasicAuthInterceptor implements ClientHttpRequestInterceptor {
    private String username;
    private String password;

    public BasicAuthInterceptor(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String encodeCredentialsForBasicAuth() {
        return "Basic " + new Base64().encodeToString((username + ":" + password).getBytes());
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest httpRequest, byte[] bytes, ClientHttpRequestExecution
            clientHttpRequestExecution) throws IOException {
        HttpHeaders headers = httpRequest.getHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, encodeCredentialsForBasicAuth());

        return clientHttpRequestExecution.execute(httpRequest, bytes);
    }
}
