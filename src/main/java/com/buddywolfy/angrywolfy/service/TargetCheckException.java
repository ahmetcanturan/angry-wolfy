package com.buddywolfy.angrywolfy.service;

/**
 * Thrown when a single "Check" request cannot be completed — a malformed URL,
 * an unreachable host, a connection error, or a timeout. The message is written
 * to be shown directly to the user.
 */
public class TargetCheckException extends RuntimeException {

    public TargetCheckException(String message) {
        super(message);
    }

    public TargetCheckException(String message, Throwable cause) {
        super(message, cause);
    }
}
