package com.hazelcast.heartattack;

import com.hazelcast.client.GenericError;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.*;
import static java.lang.String.format;

public class Coach {

    final static ILogger log = Logger.getLogger(Coach.class.getName());

    public static final String KEY_COACH = "Coach";
    public static final String COACH_HEART_ATTACK_TOPIC = "Coach:heartAttackTopic";

    public final static File heartAttackHome = getHeartAttackHome();
    public final static File traineesHome = new File(getHeartAttackHome(), "trainees");

    private File coachHzFile;
    private volatile HazelcastInstance coachHz;
    private volatile ITopic statusTopic;
    private volatile Exercise exercise;
    private List<HeartAttack> heartAttacks = Collections.synchronizedList(new LinkedList<HeartAttack>());
    private IExecutorService coachExecutor;
    private TraineeJvmManager traineeJvmManager;

    public ITopic getStatusTopic() {
        return statusTopic;
    }

    public TraineeJvmManager getTraineeJvmManager() {
        return traineeJvmManager;
    }

    public HazelcastInstance getCoachHazelcastInstance() {
        return coachHz;
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

    public void terminateWorkout(){
        log.log(Level.INFO, "Terminating workout");
        getTraineeJvmManager().destroyAll();
        log.log(Level.INFO, "Finished terminating workout");
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
        statusTopic = coachHz.getTopic(COACH_HEART_ATTACK_TOPIC);
        statusTopic.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                Object messageObject = message.getMessageObject();
                if (messageObject instanceof HeartAttack) {
                    HeartAttack heartAttack = (HeartAttack) messageObject;
                    final boolean isLocal = coachHz.getCluster().getLocalMember().getInetSocketAddress().equals(heartAttack.getCoachAddress());
                    if (isLocal) {
                        log.log(Level.SEVERE, "Local heart attack detected:" + heartAttack);
                    } else {
                        log.log(Level.SEVERE, "Remote machine heart attack detected:" + heartAttack);
                    }
                } else if (messageObject instanceof Exception) {
                    Exception e = (Exception) messageObject;
                    log.log(Level.SEVERE, e.getMessage(), e);
                } else {
                    log.log(Level.INFO, messageObject.toString());
                }
            }
        });
        coachExecutor = coachHz.getExecutorService("Coach:Executor");

        return coachHz;
    }

    public void heartAttack(HeartAttack heartAttack) {
        statusTopic.publish(heartAttack);
    }

    public void shoutToTrainees(Callable task) throws InterruptedException {
        Map<TraineeJvm, Future> futures = new HashMap<TraineeJvm, Future>();

        for (TraineeJvm traineeJvm : getTraineeJvmManager().getTraineeJvms()) {
            Member member = traineeJvm.getMember();
            if (member == null) continue;

            Future future = getTraineeJvmManager().getTraineeExecutor().submitToMember(task, member);
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

    public void start() throws Exception {
        initCoachHazelcastInstance();

        traineeJvmManager = new TraineeJvmManager(this);

        new Thread(new HeartAttackMonitor(this)).start();

        log.log(Level.INFO, "Hazelcast Assistant Coach is Ready for action");
    }

    public static void main(String[] args) throws Exception {
        log.log(Level.INFO, "Hazelcast  Coach");
        log.log(Level.INFO, format("Version: %s\n", getVersion()));
        log.log(Level.INFO, format("HEART_ATTACK_HOME: %s\n", heartAttackHome));

        OptionParser parser = new OptionParser();
        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome + File.separator + "conf" + File.separator + "coach-hazelcast.xml");

        try {
            OptionSet options = parser.parse(args);

            if (options.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            Coach coach = new Coach();
            File coachHzFile = new File(options.valueOf(coachHzFileSpec));
            if (!coachHzFile.exists()) {
                exitWithError(format("Coach Hazelcast config file [%s] does not exist\n", coachHzFile));
            }
            coach.setCoachHzFile(coachHzFile);
            coach.start();
        } catch (OptionException e) {
            exitWithError(e.getMessage() + "\nUse --help to get overview of the help options.");
        }
    }
}
