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

    public void runWorkout(Workout workout) throws Exception {
        log.log(Level.INFO, format("Exercises in workout: %s", workout.size()));
        log.log(Level.INFO, format("Running time per exercise: %s seconds", workout.getDuration()));
        log.log(Level.INFO, format("Expected total workout time: %s seconds", workout.size() * workout.getDuration()));


        //we need to make sure that before we start, there are no trainees running anymore.
        //log.log(Level.INFO, "Ensuring trainee all killed");
        stopTrainees();
        startTrainees(workout.getTraineeSettings());

        for (Exercise exercise : workout.getExerciseList()) {
            boolean success = run(workout, exercise);
            if (!success && workout.isFailFast()) {
                log.log(Level.INFO, "Aborting working due to failure");
                break;
            }

            if (!success || workout.getTraineeSettings().isRefreshJvm()) {
                stopTrainees();
                startTrainees(workout.getTraineeSettings());
            }
        }

        stopTrainees();
    }

    private boolean run(Workout workout, Exercise exercise) {
        int oldCount = heartAttacks.size();
        try {
            sendStatusUpdate(exercise.getDescription());

            sendStatusUpdate("Exercise initializing");
            submitToAllAndWait(coachExecutor, new PrepareCoachForExercise(exercise));
            shoutAndWait(new InitExercise(exercise));

            sendStatusUpdate("Exercise global setup");
            submitToOneAndWait(new GenericExerciseTask("globalSetup"));

            sendStatusUpdate("Exercise local setup");
            shoutAndWait(new GenericExerciseTask("localSetup"));

            sendStatusUpdate("Exercise task");
            shoutAndWait(new GenericExerciseTask("start"));

            sendStatusUpdate(format("Exercise running for %s seconds", workout.getDuration()));
            sleepSeconds(workout.getDuration(), "At %s seconds");

            sendStatusUpdate("Exercise stop");
            shoutAndWait(new GenericExerciseTask("stop"));

            sendStatusUpdate("Exercise global verify");
            submitToOneAndWait(new GenericExerciseTask("globalVerify"));

            sendStatusUpdate("Exercise local verify");
            shoutAndWait(new GenericExerciseTask("localVerify"));

            sendStatusUpdate("Exercise local tear down");
            shoutAndWait(new GenericExerciseTask("localTearDown"));

            sendStatusUpdate("Exercise global tear down");
            submitToOneAndWait(new GenericExerciseTask("globalTearDown"));
            return heartAttacks.size() > oldCount;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed", e);
            return false;
        }
    }

    private void sendStatusUpdate(String s) {
        try {
            statusTopic.publish(s);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to echo to all members", e);
        }
    }

    public void sleepSeconds(int seconds, String txt) {
        int period = 30;
        int big = seconds / period;
        int small = seconds % period;

        for (int k = 1; k <= big; k++) {
            Utils.sleepSeconds(period);
            sendStatusUpdate(format(txt, period * k));
        }

        Utils.sleepSeconds(small);
    }

    private void stopTrainees() throws Exception {
        sendStatusUpdate("Stopping all remaining trainees");
        submitToAllAndWait(coachExecutor, new DestroyTrainees());
        sendStatusUpdate("All remaining trainees have been terminated");
    }

    private long startTrainees(TraineeSettings traineeSettings) throws Exception {
        long startMs = System.currentTimeMillis();
        final int traineeCount = traineeSettings.getTraineeCount();
        final int totalTraineeCount = traineeCount * getCoachHz().getCluster().getMembers().size();
        log.log(Level.INFO, format("Starting a grand total of %s Trainee Java Virtual Machines", totalTraineeCount));
        submitToAllAndWait(coachExecutor, new SpawnTrainees(traineeSettings));
        long durationMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, (format("Finished starting a grand total of %s Trainees after %s ms\n", totalTraineeCount, durationMs)));
        return startMs;
    }


    private void submitToOneAndWait(Callable task) throws InterruptedException, ExecutionException {
        Future future = traineeJvmManager.getTraineeExecutor().submit(task);
        try {
            Object o = future.get();
            if (o instanceof GenericError) {
                GenericError error = (GenericError) o;
                throw new ExecutionException(error.getMessage() + ": details:" + error.getDetails(), null);
            }
        } catch (ExecutionException e) {
            heartAttack(new HeartAttack(null, null, null, null, getExercise(), e));
            throw e;
        }
    }

    private void shoutAndWait(Callable task) throws InterruptedException, ExecutionException {
        submitToAllAndWait(coachExecutor, new ShoutTask(task));
    }

    private void submitToAllAndWait(IExecutorService executorService, Callable task) throws InterruptedException, ExecutionException {
        Map<Member, Future> map = executorService.submitToAllMembers(task);
        getAllFutures(map.values());
    }

    private void getAllFutures(Collection<Future> futures) throws InterruptedException, ExecutionException {
        for (Future future : futures) {
            try {
                Object o = future.get(1000, TimeUnit.SECONDS);
                if (o instanceof GenericError) {
                    GenericError error = (GenericError) o;
                    throw new ExecutionException(error.getMessage() + ": details:" + error.getDetails(), null);
                }
            } catch (TimeoutException e) {
                heartAttack(new HeartAttack(null, null, null, null, getExercise(), e));
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                heartAttack(new HeartAttack(null, null, null, null, getExercise(), e));
                throw e;
            }
        }
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
