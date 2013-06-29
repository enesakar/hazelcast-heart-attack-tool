package com.hazelcast.heartattack;

import java.io.*;
import java.util.Properties;

public final class Utils {

    public static String getVersion() {
        String version = "";
        try {
            Properties p = new Properties();
            InputStream is = Utils.class.getResourceAsStream("/META-INF/maven/hazelcast-heartattack/hazelcast-heartattack/pom.properties");
            if (is != null) {
                p.load(is);
                return p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        }
        return version;
    }

    public static void write(File file, String text) {
        try {
            FileOutputStream stream = new FileOutputStream(file);
            try {
                Writer writer = new BufferedWriter(new OutputStreamWriter(stream));
                writer.write(text);
                writer.close();
            } finally {
                closeQuietly(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String asText(File file) {
        try {
            FileInputStream stream = new FileInputStream(file);
            try {
                Reader reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer, 0, buffer.length)) > 0) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            } finally {
                closeQuietly(stream);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static File getHeartAttackHome() {
        String heartAttackHome = System.getenv("HEART_ATTACK_HOME");
        if (heartAttackHome == null) {
            return new File(System.getProperty("user.dir"));
        } else {
            return new File(heartAttackHome);
        }
    }

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
