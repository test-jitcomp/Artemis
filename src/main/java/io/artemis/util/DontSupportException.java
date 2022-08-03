package io.artemis.util;

public class DontSupportException extends RuntimeException {

    public DontSupportException(String message) {
        super("Don't support: " + message);
    }
}
