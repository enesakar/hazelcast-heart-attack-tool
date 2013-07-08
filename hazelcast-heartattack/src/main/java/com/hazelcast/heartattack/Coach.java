package com.hazelcast.heartattack;

import com.hazelcast.client.GenericError;
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
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.closeQuietly;
import static com.hazelcast.heartattack.Utils.getHeartAttackHome;
import static java.lang.String.format;

public abstract class Coach {

    final static ILogger log = Logger.getLogger(Coach.class.getName());

    public static final String KEY_COACH = "Coach";
    public static final String COACH_HEART_ATTACK_QUEUE = "Coach:headCoachCount";

    public final static File userDir = new File(System.getProperty("user.dir"));
    public final static String classpath = System.getProperty("java.class.path");
    public final static File heartAttackHome = getHeartAttackHome();
    public final static File traineesHome = new File(getHeartAttackHome(), "trainees");

    protected File coachHzFile;
    protected volatile HazelcastInstance coachHz;
    protected volatile HazelcastInstance traineeClient;
    protected volatile IExecutorService traineeExecutor;
    protected volatile IQueue<HeartAttack> heartAttackQueue;
    protected volatile Exercise exercise;
    private final List<TraineeJvm> traineeJvms = new CopyOnWriteArrayList<TraineeJvm>();
    private final AtomicBoolean javaHomePrinted = new AtomicBoolean();

    public Coach() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                for (TraineeJvm jvm : traineeJvms) {
                    log.log(Level.INFO, "Destroying trainee : " + jvm.getId());
                    jvm.getProcess().destroy();
                }
            }
        });
    }

    public HazelcastInstance getTraineeClient() {
        return traineeClient;
    }

    public IExecutorService getTraineeExecutor() {
        return traineeExecutor;
    }

    public List<TraineeJvm> getTrainees() {
        return traineeJvms;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public void setCoachHzFile(File coachHzFile) {
        this.coachHzFile = coachHzFile;
    }

    public HazelcastInstance getCoachHz() {
        return coachHz;
    }

    public File getCoachHzFile() {
        return coachHzFile;
    }

    protected HazelcastInstance initCoachHazelcastInstance() {
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

    public void heartAttack(HeartAttack heartAttack) {
        heartAttackQueue.add(heartAttack);
    }

    public void shoutToTrainees(Callable task) throws InterruptedException {
        Map<TraineeJvm, Future> futures = new HashMap<TraineeJvm, Future>();

        for (TraineeJvm traineeJvm : traineeJvms) {
            Member member = traineeJvm.getMember();
            if (member == null) continue;

            Future future = traineeExecutor.submitToMember(task, member);
            futures.put(traineeJvm, future);
        }

        for (Map.Entry<TraineeJvm, Future> entry : futures.entrySet()) {
            TraineeJvm traineeJvm = entry.getKey();
            Future future = entry.getValue();
            try {
                Object o = future.get();
                if (o instanceof GenericError) {
                    GenericError error = (GenericError) o;
                    throw new ExecutionException(error.getMessage() + ": details:" + error.getDetails(), null);
                }
            } catch (ExecutionException e) {
                final HeartAttack heartAttack = new HeartAttack(
                        null,
                        coachHz.getCluster().getLocalMember().getInetSocketAddress(),
                        traineeJvm.getMember().getInetSocketAddress(),
                        traineeJvm.getId(),
                        exercise,
                        e);
                heartAttack(heartAttack);
            }
        }
    }


    private class HeartAttackMonitor implements Runnable {
        public void run() {
            for (; ; ) {
                for (TraineeJvm jvm : traineeJvms) {
                    HeartAttack heartAttack = null;

                    if (heartAttack == null) {
                        heartAttack = detectHeartAttackFile(jvm);
                    }

                    if (heartAttack == null) {
                        heartAttack = detectUnexpectedExit(jvm);
                    }

                    if (heartAttack == null) {
                        heartAttack = detectMembershipFailure(jvm);
                    }

                    if (heartAttack != null) {
                        traineeJvms.remove(jvm);
                        heartAttack(heartAttack);
                    }
                }

                Utils.sleepSeconds(1);
            }
        }

        private HeartAttack detectMembershipFailure(TraineeJvm jvm) {
            //if the jvm is not assigned a hazelcast address yet.
            if (jvm.getMember() == null) {
                return null;
            }

            Member member = findMember(jvm);
            if (member == null) {
                jvm.getProcess().destroy();
                return new HeartAttack("Hazelcast membership failure (member missing)",
                        coachHz.getCluster().getLocalMember().getInetSocketAddress(),
                        jvm.getMember().getInetSocketAddress(),
                        jvm.getId(),
                        exercise);
            }

            return null;
        }

        private Member findMember(TraineeJvm jvm) {
            if (traineeClient == null) return null;

            for (Member member : traineeClient.getCluster().getMembers()) {
                if (member.getInetSocketAddress().equals(jvm.getMember().getInetSocketAddress())) {
                    return member;
                }
            }

            return null;
        }

        private HeartAttack detectHeartAttackFile(TraineeJvm jvm) {
            File file = new File(traineesHome, jvm.getId() + ".heartattack");
            if (!file.exists()) {
                return null;
            }
            HeartAttack heartAttack = new HeartAttack(
                    "out of memory",
                    coachHz.getCluster().getLocalMember().getInetSocketAddress(),
                    jvm.getMember().getInetSocketAddress(),
                    jvm.getId(),
                    exercise);
            jvm.getProcess().destroy();
            return heartAttack;
        }

        private HeartAttack detectUnexpectedExit(TraineeJvm jvm) {
            Process process = jvm.getProcess();
            try {
                if (process.exitValue() != 0) {
                    return new HeartAttack(
                            "exit code not 0",
                            coachHz.getCluster().getLocalMember().getInetSocketAddress(),
                            jvm.getMember().getInetSocketAddress(),
                            jvm.getId(),
                            exercise);
                }
            } catch (IllegalThreadStateException ignore) {
            }
            return null;
        }
    }

    public void spawnTrainees(TraineeSettings settings) throws Exception {
        log.log(Level.INFO, format("Starting %s trainee Java Virtual Machines using settings %s", settings.getTraineeCount(), settings));

        File traineeHzFile = File.createTempFile("trainee-hazelcast", "xml");
        traineeHzFile.deleteOnExit();
        Utils.write(traineeHzFile, settings.getHzConfig());

        List<TraineeJvm> trainees = new LinkedList<TraineeJvm>();

        for (int k = 0; k < settings.getTraineeCount(); k++) {
            TraineeJvm trainee = startTraineeJvm(settings.getVmOptions(), traineeHzFile);
            Process process = trainee.getProcess();
            String traineeId = trainee.getId();

            trainees.add(trainee);

            new TraineeLogger(traineeId, process.getInputStream(), settings.isTrackLogging()).start();
        }
        Config config = new XmlConfigBuilder(traineeHzFile.getAbsolutePath()).build();
        ClientConfig clientConfig = new ClientConfig().addAddress("localhost:" + config.getNetworkConfig().getPort());
        clientConfig.getGroupConfig()
                .setName(config.getGroupConfig().getName())
                .setPassword(config.getGroupConfig().getPassword());
        traineeClient = HazelcastClient.newHazelcastClient(clientConfig);
        traineeExecutor = traineeClient.getExecutorService(Trainee.TRAINEE_EXECUTOR);

        for (TraineeJvm trainee : trainees) {
            waitForTraineeStartup(trainee, settings.getTraineeStartupTimeout());
        }

        log.log(Level.INFO, format("Finished starting %s trainee Java Virtual Machines", settings.getTraineeCount()));
    }

    private String getJavaHome() {
        String javaHome = System.getProperty("java.home");
        if (javaHomePrinted.compareAndSet(false, true)) {
            log.log(Level.INFO, "java.home=" + javaHome);
        }

        return javaHome;
    }

    private TraineeJvm startTraineeJvm(String traineeVmOptions, File traineeHzFile) throws IOException {
        String traineeId = "" + System.currentTimeMillis();

        String[] clientVmOptionsArray = new String[]{};
        if (traineeVmOptions != null && !traineeVmOptions.trim().isEmpty()) {
            clientVmOptionsArray = traineeVmOptions.split("\\s+");
        }

        String javaHome = getJavaHome();

        List<String> args = new LinkedList<String>();
        args.add("java");
        args.add(format("-XX:OnOutOfMemoryError=\"\"touch %s/trainees/%s.heartattack\"\"", heartAttackHome, traineeId));
        args.add("-DHEART_ATTACK_HOME=" + getHeartAttackHome());
        args.add("-Dhazelcast.logging.type=log4j");
        args.add("-DtraineeId=" + traineeId);
        args.add("-Dlog4j.configuration=file:" + heartAttackHome + File.separator + "conf" + File.separator + "trainee-log4j.xml");
        args.add("-cp");
        args.add(classpath);
        args.addAll(Arrays.asList(clientVmOptionsArray));
        args.add(Trainee.class.getName());
        args.add(traineeId);
        args.add(traineeHzFile.getAbsolutePath());

        ProcessBuilder processBuilder = new ProcessBuilder(args.toArray(new String[args.size()]))
                .directory(new File(javaHome, "bin"))
                .redirectErrorStream(true);

        Process process = processBuilder.start();
        final TraineeJvm traineeJvm = new TraineeJvm(traineeId, process);
        traineeJvms.add(traineeJvm);
        return traineeJvm;
    }

    private void waitForTraineeStartup(TraineeJvm jvm, int traineeTimeoutSec) throws InterruptedException {
        IMap<String, InetSocketAddress> traineeParticipantMap = traineeClient.getMap(Trainee.TRAINEE_PARTICIPANT_MAP);

        boolean found = false;
        for (int l = 0; l < traineeTimeoutSec; l++) {
            if (traineeParticipantMap.containsKey(jvm.getId())) {
                InetSocketAddress address = traineeParticipantMap.remove(jvm.getId());

                Member member = null;
                for (Member m : traineeClient.getCluster().getMembers()) {
                    if (m.getInetSocketAddress().equals(address)) {
                        member = m;
                        break;
                    }

                }

                if(member == null){
                    throw new RuntimeException("No member found for address: "+address);
                }

                jvm.setMember(member);
                found = true;
                break;
            } else {
                Utils.sleepSeconds(1);
            }
        }

        if (!found) {
            throw new RuntimeException(format("Timeout: trainee %s on host %s didn't start within %s seconds",
                    jvm.getId(), coachHz.getCluster().getLocalMember().getInetSocketAddress(), traineeTimeoutSec));
        }
        log.log(Level.INFO, "Trainee: " + jvm.getId() + " Started");
    }

    public void destroyTrainees() {
        if (traineeClient != null) {
            traineeClient.getLifecycleService().shutdown();
        }

        List<TraineeJvm> trainees = new LinkedList<TraineeJvm>();
        trainees.removeAll(traineeJvms);

        for (TraineeJvm jvm : trainees) {
            jvm.getProcess().destroy();
        }

        for (TraineeJvm jvm : trainees) {
            int exitCode = 0;
            try {
                exitCode = jvm.getProcess().waitFor();
            } catch (InterruptedException e) {
            }

            if (exitCode != 0) {
                log.log(Level.INFO, format("trainee process %s exited with exit code: %s", jvm.getId(), exitCode));
            }
        }
    }
}
