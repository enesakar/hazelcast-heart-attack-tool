package com.hazelcast.heartattack.tasks;

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

    private final String traineeVmOptions;
    private final boolean traineeTrackLogging;
    private final String traineeHzConfig;
    private transient HazelcastInstance hz;
    private final int traineeVmCount;

    public SpawnTraineesTask(int traineeVmCount, String traineeVmOptions, boolean traineeTrackLogging, String traineeHzConfig) {
        this.traineeVmCount = traineeVmCount;
        this.traineeVmOptions = traineeVmOptions;
        this.traineeTrackLogging = traineeTrackLogging;
        this.traineeHzConfig = traineeHzConfig;
    }

    @Override
    public Object call() throws Exception {
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

            String heartAttackHome = Utils.getHeartAttackHome().getAbsolutePath();

            for (int k = 0; k < traineeVmCount; k++) {
                String traineeId = "" + System.currentTimeMillis();
                traineeIds.add(traineeId);

                List<String> args = new LinkedList<String>();
                args.add("java");
                args.add(format("-Djava.util.logging.config.file=%s/conf/trainee-logging.properties", heartAttackHome));
                args.add("-cp");
                args.add(classpath);
                args.addAll(Arrays.asList(clientVmOptionsArray));
                args.add(Trainee.class.getName());
                args.add(traineeId);
                args.add(traineeHzFile.getAbsolutePath());

                File userDir = new File(System.getProperty("user.dir"));
                File traineeDir = new File(userDir, "trainees");
                File targetDir = new File(traineeDir, traineeId);
                if (!targetDir.mkdirs()) {
                    throw new RuntimeException("Could not create target directory: " + targetDir.getAbsolutePath());
                }

                Process process = new ProcessBuilder(args.toArray(new String[args.size()]))
                        .directory(userDir)
                        .start();
                coach.getTraineeProcesses().add(process);

                if (traineeTrackLogging) {
                    new LoggingThread(traineeId, process.getInputStream()).start();
                    new LoggingThread(traineeId, process.getErrorStream()).start();
                }
            }

            for (String traineeId : traineeIds) {
                waitForTraineeStartup(coach, traineeId);
            }

            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to spawn Trainee Virtual Machines", e);
            throw e;
        }
    }

    private void waitForTraineeStartup(Coach coach, String id) throws InterruptedException {
        IMap<String, String> traineeParticipantMap = coach.getTraineeHazelcastClient().getMap(Trainee.TRAINEE_PARTICIPANT_MAP);

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

        public LoggingThread(String prefix, InputStream inputStream) {
            this.inputStream = inputStream;
            this.prefix = prefix;
        }

        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                for (; ; ) {
                    final String line = br.readLine();
                    if (line == null) break;
                    if (log.isLoggable(Level.INFO)) {
                        log.log(Level.INFO, prefix + ": " + line);
                    }
                }
            } catch (IOException e) {
            }
        }
    }
}
