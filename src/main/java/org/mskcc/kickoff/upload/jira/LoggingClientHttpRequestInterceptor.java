package org.mskcc.kickoff.upload.jira;

import org.apache.log4j.Logger;
import org.mskcc.kickoff.util.Constants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class LoggingClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger LOGGER = Logger.getLogger(Constants.DEV_LOGGER);

    private volatile boolean loggedMissingBuffering;

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        logRequest(request, body);
        ClientHttpResponse response = execution.execute(request, body);
        logResponse(request, response);

        return response;
    }

    protected void logRequest(HttpRequest request, byte[] body) {
        StringBuilder builder = new StringBuilder("Sending ").append(request.getMethod()).append(" request to ")
                .append(request.getURI());
        if (body.length > 0 && hasTextBody(request.getHeaders())) {
            String bodyText = new String(body, StandardCharsets.UTF_8);
            builder.append(": [").append(bodyText).append("]");
        }
        LOGGER.info(builder.toString());
    }

    protected boolean hasTextBody(HttpHeaders headers) {
        MediaType contentType = headers.getContentType();
        if (contentType != null) {
            String subtype = contentType.getSubtype();
            return "text".equals(contentType.getType()) || "xml".equals(subtype) || "json".equals(subtype);
        }
        return false;
    }

    protected void logResponse(HttpRequest request, ClientHttpResponse response) {
//        if (LOGGER.isDebugEnabled()) {
        try {
            StringBuilder builder = new StringBuilder("Received \"")
                    .append(response.getRawStatusCode()).append(" ").append(response.getStatusText()).append("\" " +
                            "response for ")
                    .append(request.getMethod()).append(" request to ").append(request.getURI());
            HttpHeaders responseHeaders = response.getHeaders();
            long contentLength = responseHeaders.getContentLength();
            if (contentLength != 0) {
                if (hasTextBody(responseHeaders) && isBuffered(response)) {
                    String bodyText = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                    builder.append(": [").append(bodyText).append("]");
                } else {
                    if (contentLength == -1) {
                        builder.append(" with content of unknown length");
                    } else {
                        builder.append(" with content of length ").append(contentLength);
                    }
                    MediaType contentType = responseHeaders.getContentType();
                    if (contentType != null) {
                        builder.append(" and content type ").append(contentType);
                    } else {
                        builder.append(" and unknown context type");
                    }
                }
            }

            builder.append(response.getBody());

            LOGGER.info(builder.toString());
        } catch (IOException e) {
            LOGGER.warn(String.format("Failed to log response for %s request to %s", request.getMethod(), request
                    .getURI()), e);
        }
//        }
    }

    protected boolean isBuffered(ClientHttpResponse response) {
        // class is non-public, so we check by name
        boolean buffered = "org.springframework.http.client.BufferingClientHttpResponseWrapper".equals(response
                .getClass().getName());
        if (!buffered && !loggedMissingBuffering) {
            LOGGER.warn("Can't log HTTP response bodies, as you haven't configured the RestTemplate with a " +
                    "BufferingClientHttpRequestFactory");
            loggedMissingBuffering = true;
        }
        return buffered;
    }
}
