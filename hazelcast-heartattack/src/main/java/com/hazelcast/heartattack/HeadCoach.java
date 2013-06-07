package com.hazelcast.heartattack;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.GenericExerciseTask;
import com.hazelcast.heartattack.tasks.InitExerciseTask;
import com.hazelcast.heartattack.tasks.ShutdownTask;
import com.hazelcast.heartattack.tasks.SpawnTrainees;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.hazelcast.heartattack.Utils.sleepSeconds;
import static java.lang.String.format;

public class HeadCoach implements Coach {

    private final static Logger log = Logger.getLogger(HeadCoach.class.getName());

    private HazelcastInstance coachHz;
    private HazelcastInstance traineeHz;
    private IExecutorService traineeExecutor;
    private IExecutorService coachExecutor;
    private int durationSec;
    private String traineeVmOptions;
    private Workout workout;
    private int traineeVmCount;

    public void setWorkout(Workout workout) {
        this.workout = workout;
    }

    public HazelcastInstance getCoachHazelcastInstance() {
        return coachHz;
    }

    public HazelcastInstance getTraineeHazelcastInstance() {
        return traineeHz;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public void setTraineeVmOptions(String traineeVmOptions) {
        this.traineeVmOptions = traineeVmOptions;
    }

    public String getTraineeVmOptions() {
        return traineeVmOptions;
    }

    public void setTraineeVmCount(int traineeCount) {
        this.traineeVmCount = traineeCount;
    }

    public int getTraineeVmCount() {
        return traineeVmCount;
    }

    private HazelcastInstance createCoachHazelcastInstance() {
        Config config = new Config();
        config.getUserContext().put("Coach", this);
        config.getGroupConfig().setName("coach");
        return Hazelcast.newHazelcastInstance(config);
    }

    private void start() throws InterruptedException, ExecutionException {
        System.out.printf("Exercises: %s\n", workout.size());
        System.out.printf("Expected running time: %s seconds\n",workout.size()*durationSec);
        System.out.printf("Trainee's per coach: %s.\n", traineeVmCount);

        long startMs = System.currentTimeMillis();

        this.coachHz = createCoachHazelcastInstance();
        this.coachExecutor = coachHz.getExecutorService("Coach:Executor");

        this.traineeHz = Trainee.createHazelcastInstance();
        this.traineeExecutor = traineeHz.getExecutorService(Trainee.TRAINEE_EXECUTOR);

        signalHeadCoachAvailable();

        log.info(format("Starting %s trainee Java Virtual Machines", traineeVmCount));
        submitToAllAndWait(coachExecutor, new SpawnTrainees(traineeVmCount, traineeVmOptions));
        long durationMs = System.currentTimeMillis() - startMs;
        log.info(format("Trainee Java Virtual Machines have started after %s ms\n", durationMs));

        for (Exercise exercise : workout.getExerciseList()) {
            doExercise(exercise);
        }

        submitToAllAndWait(traineeExecutor, new ShutdownTask());

        long elapsedMs = System.currentTimeMillis()-startMs;
        System.out.printf("Total running time: %s\n",elapsedMs/1000);
    }

    private void doExercise(Exercise exercise) {
        try {
            log.info("Exercise initializing");
            submitToAllAndWait(traineeExecutor, new InitExerciseTask(exercise));

            log.info("Exercise global setup");
            submitToOneAndWait(new GenericExerciseTask("globalSetup"));

            log.info("Exercise local setup");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localSetup"));

            log.info("Exercise task");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("start"));

            log.info(format("Exercise running for %s seconds", durationSec));
            sleepSeconds(durationSec);

            log.info("Exercise stop");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("stop"));

            log.info("Exercise global verify");
            submitToOneAndWait(new GenericExerciseTask("globalVerify"));

            log.info("Exercise local verify");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localVerify"));

            log.info("Exercise local tear down");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localTearDown"));

            log.info("Exercise global tear down");
            submitToOneAndWait(new GenericExerciseTask("globalTearDown"));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed", e);
        }
    }

    private void submitToOneAndWait(Callable task) throws InterruptedException, ExecutionException {
        traineeExecutor.submit(task).get();
    }

    private void submitToAllAndWait(IExecutorService executorSerivce, Callable task) throws InterruptedException, ExecutionException {
        Map<Member, Future> map = executorSerivce.submitToAllMembers(task);
        getAllFutures(map.values());
    }

    private void getAllFutures(Collection<Future> futures) throws InterruptedException, ExecutionException {
        for (Future future : futures) {
            future.get();
        }
    }

    private void signalHeadCoachAvailable() {
        ILock lock = coachHz.getLock("Coach:headCoachLock");
        lock.lock();
        ICondition condition = lock.newCondition("Coach:headCoachCondition");
        IAtomicLong available = coachHz.getAtomicLong("Coach:headCoachCount");

        try {
            available.incrementAndGet();
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Heart Attack Coach");

        OptionParser parser = new OptionParser();
        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to start)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec<Integer> traineeCountSpec = parser.accepts("traineeVmCount", "Number of Trainee VM's per Coach")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        OptionSpec<String> traineeVmOptionsSpec = parser.accepts("traineeVmOptions", "Trainee VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("");
        OptionSpec helpSpec =
                parser.accepts("help", "Show help").forHelp();

        OptionSet set;
        try {
            set = parser.parse(args);
            if (set.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            String workoutFileName = "workout.json";
            List<String> workoutFiles = set.nonOptionArguments();
            if (workoutFiles.size() == 1) {
                workoutFileName = workoutFiles.get(0);
            } else if (workoutFiles.size() > 1) {
                System.out.println("Too many workout files specified.");
                System.exit(0);
            }

            HeadCoach coach = new HeadCoach();

            File workoutJsonFile = new File(workoutFileName);
            Workout workout = createWorkout(workoutJsonFile);
            coach.setWorkout(workout);
            coach.setDurationSec(set.valueOf(durationSpec));
            coach.setTraineeVmOptions(set.valueOf(traineeVmOptionsSpec));
            coach.setTraineeVmCount(set.valueOf(traineeCountSpec));

            coach.start();
        } catch (OptionException e) {
            System.out.println(e.getMessage() + ". Use --help to get overview of the help options.");
            System.exit(1);
        }
    }

    // http://programmerbruce.blogspot.com/2011/05/deserialize-json-with-jackson-into.html
    private static Workout createWorkout(File file) throws Exception {
        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper(jsonFactory);

        JsonParser parser = jsonFactory.createParser(file);
        mapper.enableDefaultTyping();
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);

        Collection<Exercise> exercises = mapper.readValue(parser, new TypeReference<Collection<Exercise>>() {
        });

        Workout workout = new Workout();
        workout.getExerciseList().addAll(exercises);
        return workout;
    }

}
