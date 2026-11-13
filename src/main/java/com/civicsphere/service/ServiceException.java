package com.civicsphere.service;

/**
 * Domain/application exception mapped to safe HTTP status codes by controllers.
 */
public class ServiceException extends RuntimeException {
    private final int statusCode;

    public ServiceException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ServiceException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

