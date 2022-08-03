package io.artemis;

public class AxChecker {

    public static class CheckFailError extends Error {
        public CheckFailError(String message) {
            super("Check fail: " + message);
        }
    }

    public static void check(boolean value, String message) {
        if (!value) {
            throw new CheckFailError(message);
        }
    }
}
