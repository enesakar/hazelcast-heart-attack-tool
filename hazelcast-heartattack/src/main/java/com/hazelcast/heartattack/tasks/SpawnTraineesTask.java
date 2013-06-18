package com.hazelcast.heartattack.tasks;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.heartattack.Trainee;
import com.hazelcast.heartattack.Utils;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import static java.lang.String.format;

public class SpawnTraineesTask implements Callable, Serializable, HazelcastInstanceAware {
    final static ILogger log = Logger.getLogger(InitExerciseTask.class.getName());

    private transient HazelcastInstance hz;

    private String traineeVmOptions;
    private boolean traineeTrackLogging;
    private String traineeHzConfig;
    private int traineeVmCount;

    public void setTraineeHzConfig(String traineeHzConfig) {
        this.traineeHzConfig = traineeHzConfig;
    }

    public void setTraineeTrackLogging(boolean traineeTrackLogging) {
        this.traineeTrackLogging = traineeTrackLogging;
    }

    public void setTraineeVmCount(int traineeVmCount) {
        this.traineeVmCount = traineeVmCount;
    }

    public void setTraineeVmOptions(String traineeVmOptions) {
        this.traineeVmOptions = traineeVmOptions;
    }

    @Override
    public Object call() throws Exception {
        log.log(Level.INFO, format("Spawning %s trainees", traineeVmCount));
        long startMs = System.currentTimeMillis();

        try {
            Coach coach = (Coach) hz.getUserContext().get(Coach.KEY_COACH);

            String classpath = System.getProperty("java.class.path");
            String[] clientVmOptionsArray = new String[]{};
            if (traineeVmOptions != null && !traineeVmOptions.trim().isEmpty()) {
                clientVmOptionsArray = traineeVmOptions.split("\\s+");
            }

            List<String> traineeIds = new LinkedList<String>();

            File traineeHzFile = File.createTempFile("trainee-hazelcast", "xml");
            traineeHzFile.deleteOnExit();
            Utils.write(traineeHzFile, traineeHzConfig);

            Config config = new XmlConfigBuilder(traineeHzFile.getAbsolutePath()).build();

            for (int k = 0; k < traineeVmCount; k++) {
                String traineeId = "" + System.currentTimeMillis();
                traineeIds.add(traineeId);

                List<String> args = new LinkedList<String>();
                args.add("java");
                args.add("-cp");
                args.add(classpath);
                args.addAll(Arrays.asList(clientVmOptionsArray));
                args.add(Trainee.class.getName());
                args.add(traineeId);
                args.add(traineeHzFile.getAbsolutePath());

                File userDir = new File(System.getProperty("user.dir"));

                ProcessBuilder processBuilder = new ProcessBuilder(args.toArray(new String[args.size()]))
                        .directory(userDir)
                        .redirectErrorStream(true);
                Process process = processBuilder.start();

                coach.getTraineeProcesses().add(process);

                new LoggingThread(traineeId, process.getInputStream(), traineeTrackLogging).start();
            }

            coach.initTraineeClient(config);

            for (String traineeId : traineeIds) {
                waitForTraineeStartup(coach, traineeId);
            }

            long durationMs = System.currentTimeMillis() - startMs;
            log.log(Level.INFO, format("Spawned %s trainees in %s ms", traineeVmCount, durationMs));

            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to spawn Trainee Virtual Machines", e);
            throw e;
        }
    }

    private void waitForTraineeStartup(Coach coach, String id) throws InterruptedException {
        IMap<String, String> traineeParticipantMap = coach.getTraineeClient().getMap(Trainee.TRAINEE_PARTICIPANT_MAP);

        boolean found = false;
        for (int l = 0; l < 300; l++) {
            if (traineeParticipantMap.containsKey(id)) {
                traineeParticipantMap.remove(id);
                found = true;
                break;
            } else {
                Utils.sleepSeconds(1);
            }
        }

        if (!found) {
            throw new RuntimeException(format("Trainee %s didn't start up in time", id));
        }
        log.log(Level.INFO, "Trainee: " + id + " Started");
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }

    private static class LoggingThread extends Thread {
        private final static ILogger log = Logger.getLogger(LoggingThread.class.getName());

        private final InputStream inputStream;
        private final String prefix;
        private final boolean traineeTrackLogging;

        public LoggingThread(String prefix, InputStream inputStream, boolean traineeTrackLogging) {
            this.inputStream = inputStream;
            this.prefix = prefix;
            this.traineeTrackLogging = traineeTrackLogging;
        }

        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                for (; ; ) {
                    final String line = br.readLine();
                    if (line == null) break;
                    if (log.isLoggable(Level.INFO) && traineeTrackLogging) {
                        log.log(Level.INFO, prefix + ": " + line);
                    }
                }
            } catch (IOException e) {
            }
        }
    }
}
