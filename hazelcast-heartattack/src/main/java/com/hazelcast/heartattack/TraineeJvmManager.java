package com.hazelcast.heartattack;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.core.IMap;
import com.hazelcast.core.Member;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.getHeartAttackHome;
import static java.lang.String.format;

public class TraineeJvmManager {

    final static ILogger log = Logger.getLogger(TraineeJvmManager.class);
    private final AtomicBoolean javaHomePrinted = new AtomicBoolean();
    public final static File userDir = new File(System.getProperty("user.dir"));
    public final static String classpath = System.getProperty("java.class.path");
    public final static File heartAttackHome = getHeartAttackHome();
    public final static File traineesHome = new File(getHeartAttackHome(), "trainees");

    private final List<TraineeJvm> traineeJvms = new CopyOnWriteArrayList<TraineeJvm>();
    private final Coach coach;
    private HazelcastInstance traineeClient;
    private IExecutorService traineeExecutor;

    public TraineeJvmManager(Coach coach) {
        this.coach = coach;

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                for (TraineeJvm jvm : traineeJvms) {
                    log.log(Level.INFO, "Destroying trainee : " + jvm.getId());
                    jvm.getProcess().destroy();
                }
            }
        });
    }

    public List<TraineeJvm> getTraineeJvms() {
        return traineeJvms;
    }

    public void spawn(TraineeSettings settings) throws Exception {
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

                if (member == null) {
                    throw new RuntimeException("No member found for address: " + address);
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
                    jvm.getId(), coach.getCoachHz().getCluster().getLocalMember().getInetSocketAddress(), traineeTimeoutSec));
        }
        log.log(Level.INFO, "Trainee: " + jvm.getId() + " Started");
    }

    public void destroyAll() {
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

    public IExecutorService getTraineeExecutor() {
        return traineeExecutor;
    }

    public HazelcastInstance getTraineeClient() {
        return traineeClient;
    }

    public void destroy(TraineeJvm jvm) {
        jvm.getProcess().destroy();
        traineeJvms.remove(jvm);
    }
}
