package io.artemis.util;

public class CannotReachHereException extends RuntimeException {

    public CannotReachHereException(String message) {
        super("Cannot reach here: " + message);
    }

    public CannotReachHereException() {
        super("Cannot reach here");
    }
}
