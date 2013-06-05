package com.hazelcast.heartattack;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.exercises.MapExercise;
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
import java.util.logging.Logger;

import static com.hazelcast.heartattack.Utils.sleepSeconds;

public class Coach {

    private final static Logger log = Logger.getLogger(Coach.class.getName());

    private HazelcastInstance coachHz;
    private HazelcastInstance traineeHz;
    private IExecutorService traineeExecutor;
    private IExecutorService coachExecutor;
    private boolean isHeadCoach;
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

    public void setIsHeadCoach(boolean headCoach) {
        isHeadCoach = headCoach;
    }

    public boolean isHeadCoach() {
        return isHeadCoach;
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

    private void run() throws InterruptedException, ExecutionException {
        this.coachHz = createCoachHazelcastInstance();
        this.coachExecutor = coachHz.getExecutorService("Coach:Executor");

        this.traineeHz = Trainee.createHazelcastInstance();
        this.traineeExecutor = traineeHz.getExecutorService(Trainee.TRAINEE_EXECUTOR);


        if (!isHeadCoach) {
            System.out.println("Starting assistant coach");
            awaitHeadCoachAvailable();
        } else {
            System.out.println("Starting head coach");
            System.out.println("traineeVmOptions: " + traineeVmOptions);


            signalHeadCoachAvailable();

            log.info("Starting trainee Java Virtual Machines");
            submitToAllAndWait(coachExecutor, new SpawnTrainees(traineeVmCount, traineeVmOptions));
            log.info("All trainee Java Virtual Machines have started");

            for (Exercise exercise : workout.getExerciseList()) {
                doExercise(exercise);
            }

            submitToAllAndWait(traineeExecutor, new ShutdownTask());
        }
    }

    private void doExercise(Exercise exercise) throws InterruptedException, ExecutionException {
        submitToAllAndWait(traineeExecutor, new InitExerciseTask(exercise));

        submitToOneAndWait(new GenericExerciseTask("globalSetup"));

        submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localSetup"));

        submitToAllAndWait(traineeExecutor, new GenericExerciseTask("start"));

        sleepSeconds(durationSec);

        submitToAllAndWait(traineeExecutor, new GenericExerciseTask("stop"));

        submitToOneAndWait(new GenericExerciseTask("globalVerify"));

        submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localVerify"));

        submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localTearDown"));

        submitToOneAndWait(new GenericExerciseTask("globalTearDown"));
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

    private void awaitHeadCoachAvailable() {
        log.info("Awaiting Head Coach");

        ILock lock = coachHz.getLock("Coach:headCoachLock");
        lock.lock();
        ICondition condition = lock.newCondition("Coach:headCoachCondition");
        IAtomicLong available = coachHz.getAtomicLong("Coach:headCoachCount");

        try {
            while (available.get() == 0) {
                condition.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            lock.unlock();
        }

        log.info("Head Coach Arrived");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Heart Attack Coach");

        OptionParser parser = new OptionParser();
        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to run)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec<Integer> traineeCountSpec = parser.accepts("traineeVmCount", "Number of Trainee VM's per Coach")
                .withRequiredArg().ofType(Integer.class).defaultsTo(4);
        OptionSpec<String> traineeVmOptionsSpec = parser.accepts("traineeVmOptions", "Trainee VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("");
        OptionSpec isHeadCoachSpec =
                parser.accepts("headCoach", "If the node is a head coach");
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
            } else if(workoutFiles.size()>1){
                System.out.println("Too many workout files specified.");
                System.exit(0);
            }


            Coach main = new Coach();
            main.setIsHeadCoach(set.has(isHeadCoachSpec));

            if (main.isHeadCoach) {
                File workoutJsonFile = new File(workoutFileName);
                Workout workout = createWorkout(workoutJsonFile);
                main.setWorkout(workout);
                main.setDurationSec(set.valueOf(durationSpec));
                main.setTraineeVmOptions(set.valueOf(traineeVmOptionsSpec));
                main.setTraineeVmCount(set.valueOf(traineeCountSpec));
            }

            main.run();
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

        for (Exercise exercise : exercises) {
            System.out.println(exercise.getClass());
        }

        Workout workout = new Workout();
        workout.getExerciseList().addAll(exercises);
        return workout;
    }

}
