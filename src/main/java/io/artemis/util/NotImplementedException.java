package io.artemis.util;

public class NotImplementedException extends RuntimeException {

    public NotImplementedException(String message) {
        super("Not implemented: " + message);
    }
}
