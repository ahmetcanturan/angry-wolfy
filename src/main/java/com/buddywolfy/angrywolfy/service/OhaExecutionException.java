package com.buddywolfy.angrywolfy.service;

/**
 * Thrown when an oha run cannot be started, is interrupted, times out, exits
 * with a non-zero status, or produces output that cannot be parsed.
 */
public class OhaExecutionException extends RuntimeException {

    public OhaExecutionException(String message) {
        super(message);
    }

    public OhaExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
