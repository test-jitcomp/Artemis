package io.artemis;

public class AxNames {
    private static AxNames sInstance;
    private int mCount;

    public static AxNames getInstance() {
        if (sInstance == null) {
            sInstance = new AxNames();
        }
        return sInstance;
    }

    public String nextName() {
        return "ax$" + (mCount++);
    }

    private AxNames() {
        mCount = 0;
    }
}
