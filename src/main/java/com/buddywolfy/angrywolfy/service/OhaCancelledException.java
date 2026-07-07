package com.buddywolfy.angrywolfy.service;

/**
 * Thrown when a run was deliberately cancelled via the run registry, as opposed
 * to failing on its own. Lets the controller return a distinct "cancelled"
 * response rather than a generic error.
 */
public class OhaCancelledException extends RuntimeException {

    public OhaCancelledException(String message) {
        super(message);
    }
}
