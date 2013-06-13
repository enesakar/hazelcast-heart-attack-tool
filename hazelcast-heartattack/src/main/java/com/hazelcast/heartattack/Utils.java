package com.hazelcast.heartattack;

import java.io.Closeable;
import java.io.IOException;

public final class Utils {

    public static void closeQuietly(Closeable c) {
        if (c == null) return;
        try {
            c.close();
        } catch (IOException ignore) {
        }
    }

    public static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void exitWithError(String msg) {
        System.out.printf(msg);
        System.exit(1);
    }


    private Utils() {
    }
}
