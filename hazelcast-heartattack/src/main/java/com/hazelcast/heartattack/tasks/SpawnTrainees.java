package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.hazelcast.heartattack.Coach;
import com.hazelcast.heartattack.Trainee;
import com.hazelcast.heartattack.Utils;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

public class SpawnTrainees implements Callable, Serializable, HazelcastInstanceAware {
    private final static Logger log = Logger.getLogger(SpawnTrainees.class.getName());

    private final String traineeVmOptions;
    private transient HazelcastInstance hz;
    private final int count;

    public SpawnTrainees(int count, String traineeVmOptions) {
        this.count = count;
        this.traineeVmOptions = traineeVmOptions;
    }

    @Override
    public Object call() throws Exception {
        try {
            Coach coach = (Coach) hz.getUserContext().get("Coach");

            String classpath = System.getProperty("java.class.path");
            String[] clientVmOptionsArray = new String[]{};
            if (traineeVmOptions != null && !traineeVmOptions.trim().isEmpty()) {
                clientVmOptionsArray = traineeVmOptions.split("\\s+");
            }

            List<String> traineeIds = new LinkedList<String>();

            for (int k = 0; k < count; k++) {
                String traineeId = UUID.randomUUID().toString();
                traineeIds.add(traineeId);

                List<String> args = new LinkedList<String>();
                args.add("java");
                args.add("-cp");
                args.add(classpath);
                args.addAll(Arrays.asList(clientVmOptionsArray));
                args.add(Trainee.class.getName());
                args.add(traineeId);

                Process process = new ProcessBuilder(args.toArray(new String[args.size()]))
                        .directory(new File(System.getProperty("user.dir")))
                        .start();
                new LoggingThread(traineeId, process.getInputStream()).start();
                new LoggingThread(traineeId, process.getErrorStream()).start();
            }

            for (String traineeId : traineeIds) {
                waitForTraineeStartup(coach, traineeId);
            }

            return null;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to spawn Trainee Virtual Machines",e);
            throw e;
        }
    }

    private void waitForTraineeStartup(Coach coach, String id) throws InterruptedException {
        IMap<String, String> traineeParticipantMap = coach.getTraineeHazelcastInstance().getMap(Trainee.TRAINEE_PARTICIPANT_MAP);

        boolean found = false;
        for (int l = 0; l < 180; l++) {
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
        } else {
            log.info("Trainee: " + id + " Started");
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }

    private static class LoggingThread extends Thread {
        private final static Logger log = Logger.getLogger(LoggingThread.class.getName());

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
                    System.out.println(prefix + ": " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
                ;
                // log.log(Level.SEVERE, "Failed to log", e);
            }
        }
    }
}
