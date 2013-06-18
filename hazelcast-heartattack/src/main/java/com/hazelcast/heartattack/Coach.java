package com.hazelcast.heartattack;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.closeQuietly;
import static java.lang.String.format;

public abstract class Coach {

    final static ILogger log = Logger.getLogger(Coach.class.getName());

    public static final String KEY_COACH = "Coach";
    public static final String COACH_HEAD_COACH_LOCK = "Coach:headCoachLock";
    public static final String COACH_HEAD_COACH_CONDITION = "Coach:headCoachCondition";
    public static final String COACH_HEART_ATTACK_QUEUE = "Coach:headCoachCount";
    public static final String COACH_HEAD_COACH_COUNT = "Coach:heartAttackQueue";

    protected File coachHzFile;
    protected volatile HazelcastInstance coachHz;

    protected volatile HazelcastInstance traineeClient;
    protected volatile IExecutorService traineeExecutor;
    protected volatile IQueue<HeartAttack> heartAttackQueue;
    private final List<TraineeJvm> traineeJvms = Collections.synchronizedList(new LinkedList<TraineeJvm>());
    private final File userDir = new File(System.getProperty("user.dir"));
    private final String classpath = System.getProperty("java.class.path");

    public void setCoachHzFile(File coachHzFile) {
        this.coachHzFile = coachHzFile;
    }

    public File getCoachHzFile() {
        return coachHzFile;
    }

    protected HazelcastInstance createCoachHazelcastInstance() {
        FileInputStream in;
        try {
            in = new FileInputStream(coachHzFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        Config config;
        try {
            config = new XmlConfigBuilder(in).build();
        } finally {
            closeQuietly(in);
        }
        config.getUserContext().put(KEY_COACH, this);
        coachHz = Hazelcast.newHazelcastInstance(config);
        heartAttackQueue = coachHz.getQueue(COACH_HEART_ATTACK_QUEUE);
        new Thread(new HeartAttackMonitor()).start();
        return coachHz;
    }

    private class HeartAttackMonitor implements Runnable{
        public void run() {
            while (true) {
                for (TraineeJvm jvm : traineeJvms) {
                    File file = new File(jvm.getId() + ".heartattack");
                    if (file.exists()) {
                        HeartAttack heartAttack = new HeartAttack("out of memory", coachHz.getCluster().getLocalMember().getInetSocketAddress(), jvm.getId());
                        heartAttackQueue.add(heartAttack);
                        jvm.getProcess().destroy();
                        try {
                            jvm.getProcess().waitFor();
                        } catch (InterruptedException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }

                        traineeJvms.remove(jvm);
                    }
                }

                Utils.sleepSeconds(1);
            }
        }
    }

    public void spawnTrainees(TraineeSettings settings) throws Exception {
        File traineeHzFile = File.createTempFile("trainee-hazelcast", "xml");
        traineeHzFile.deleteOnExit();
        Utils.write(traineeHzFile, settings.getHzConfig());

        List<String> traineeIds = new LinkedList<String>();

        for (int k = 0; k < settings.getTraineeVmCount(); k++) {
            TraineeJvm jvm = startTraineeJvm(settings.getVmOptions(), traineeHzFile);
            Process process = jvm.getProcess();
            String traineeId = jvm.getId();

            traineeIds.add(traineeId);

            new LoggingThread(traineeId, process.getInputStream(), settings.isTrackLogging()).start();
        }

        Config config = new XmlConfigBuilder(traineeHzFile.getAbsolutePath()).build();
        ClientConfig clientConfig = new ClientConfig().addAddress("localhost:" + config.getNetworkConfig().getPort());
        clientConfig.getGroupConfig()
                .setName(config.getGroupConfig().getName())
                .setPassword(config.getGroupConfig().getPassword());
        traineeClient = HazelcastClient.newHazelcastClient(clientConfig);
        traineeExecutor = traineeClient.getExecutorService(Trainee.TRAINEE_EXECUTOR);

        for (String traineeId : traineeIds) {
            waitForTraineeStartup(traineeId);
        }
    }

    private TraineeJvm startTraineeJvm(String traineeVmOptions, File traineeHzFile) throws IOException {

        String traineeId = "" + System.currentTimeMillis();

        String[] clientVmOptionsArray = new String[]{};
        if (traineeVmOptions != null && !traineeVmOptions.trim().isEmpty()) {
            clientVmOptionsArray = traineeVmOptions.split("\\s+");
        }

        List<String> args = new LinkedList<String>();
        args.add("java");
        args.add(format("-XX:OnOutOfMemoryError=\"\"touch %s.heartattack\"\"", traineeId));
        args.add("-cp");
        args.add(classpath);
        args.addAll(Arrays.asList(clientVmOptionsArray));
        args.add(Trainee.class.getName());
        args.add(traineeId);
        args.add(traineeHzFile.getAbsolutePath());

        ProcessBuilder processBuilder = new ProcessBuilder(args.toArray(new String[args.size()]))
                .directory(userDir)
                .redirectErrorStream(true);
        Process process = processBuilder.start();
        //  return process;
        final TraineeJvm traineeJvm = new TraineeJvm(traineeId, process);
        traineeJvms.add(traineeJvm);
        return traineeJvm;
    }

    private void waitForTraineeStartup(String id) throws InterruptedException {
        IMap<String, String> traineeParticipantMap = traineeClient.getMap(Trainee.TRAINEE_PARTICIPANT_MAP);

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


    public void destroyTrainees() {
        if (traineeClient == null)
            traineeClient.getLifecycleService().shutdown();


        for (TraineeJvm jvm : traineeJvms) {
            jvm.getProcess().destroy();
        }

        for (TraineeJvm jvm : traineeJvms) {
            int exitCode = 0;
            try {
                exitCode = jvm.getProcess().waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            if (exitCode != 0) {
                log.log(Level.INFO, format("trainee process exited with exit code: ", exitCode));
            }
        }
        traineeJvms.clear();
    }
}
