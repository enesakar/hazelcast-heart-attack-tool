package com.hazelcast.heartattack;

import java.io.*;
import java.net.URI;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static java.lang.String.format;

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

    public static byte[] zip(File directory) throws IOException {
        System.out.println("directory:"+directory);
        URI base = directory.toURI();
        Deque<File> queue = new LinkedList<File>();
        queue.push(directory);
        ByteArrayOutputStream out = new ByteArrayOutputStream(10 * 1000 * 1000);
        Closeable res = out;
        try {
            ZipOutputStream zout = new ZipOutputStream(out);
            res = zout;
            while (!queue.isEmpty()) {
                directory = queue.pop();
                for (File kid : directory.listFiles()) {
                    String name = base.relativize(kid.toURI()).getPath();
                    if (kid.isDirectory()) {
                        queue.push(kid);
                        name = name.endsWith("/") ? name : name + "/";
                        zout.putNextEntry(new ZipEntry(name));
                    } else if (!kid.getName().equals(".DS_Store")) {
                        zout.putNextEntry(new ZipEntry(name));
                        copy(kid, zout);
                        zout.closeEntry();
                    }
                }
            }
        } finally {
            res.close();
        }
        return out.toByteArray();
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        while (true) {
            int readCount = in.read(buffer);
            if (readCount < 0) {
                break;
            }
            out.write(buffer, 0, readCount);
        }
    }

    private static void copy(File file, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            copy(in, out);
        } finally {
            in.close();
        }
    }

    public static void unzip(byte[] content, final File destinationDir) throws IOException {

        byte[] buffer = new byte[1024];

        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content));
        ZipEntry zipEntry = zis.getNextEntry();

        while (zipEntry != null) {

            String fileName = zipEntry.getName();
            File file = new File(destinationDir + File.separator + fileName);
            System.out.println("file unzip : " + file.getAbsoluteFile());

            if (zipEntry.isDirectory()) {
                file.mkdirs();
            } else {

                file.getParentFile().mkdirs();
                file.createNewFile();

                FileOutputStream fos = new FileOutputStream(file);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    fos.write(buffer, 0, len);
                }
                fos.close();
            }

            zipEntry = zis.getNextEntry();
        }

        zis.closeEntry();
        zis.close();
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
