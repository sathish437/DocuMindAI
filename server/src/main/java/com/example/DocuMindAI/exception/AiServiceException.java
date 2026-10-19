package com.example.DocuMindAI.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an upstream AI service call fails.
 * Encapsulates the HTTP status and safe client message to return to clients,
 * while preserving the underlying provider cause chain for server-side diagnostics.
 */
public class AiServiceException extends IllegalStateException {

    private final HttpStatus status;
    private final String clientMessage;
    private final boolean retryable;

    public AiServiceException(HttpStatus status, String clientMessage, Throwable cause, boolean retryable) {
        super(clientMessage, cause);
        this.status = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        this.clientMessage = clientMessage;
        this.retryable = retryable;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getClientMessage() {
        return clientMessage;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
