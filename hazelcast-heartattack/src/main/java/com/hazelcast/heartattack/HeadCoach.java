package com.hazelcast.heartattack;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.DestroyTraineesTask;
import com.hazelcast.heartattack.tasks.GenericExerciseTask;
import com.hazelcast.heartattack.tasks.InitExerciseTask;
import com.hazelcast.heartattack.tasks.SpawnTraineesTask;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
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

import static com.hazelcast.heartattack.Utils.exitWithError;
import static com.hazelcast.heartattack.Utils.sleepSeconds;
import static java.lang.String.format;

public class HeadCoach extends Coach {
    final static ILogger log = Logger.getLogger(HeadCoach.class.getName());

    private IExecutorService coachExecutor;
    private int durationSec;
    private String traineeVmOptions;
    private Workout workout;
    private int traineeVmCount;
    private boolean traineeTrackLogging;
    private File traineeHzFile;

    public void setWorkout(Workout workout) {
        this.workout = workout;
    }

    public HazelcastInstance getCoachHazelcastInstance() {
        return coachHz;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public boolean isTraineeTrackLogging() {
        return traineeTrackLogging;
    }

    public void setTraineeTrackLogging(boolean traineeTrackLogging) {
        this.traineeTrackLogging = traineeTrackLogging;
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

    public void setTraineeHzFile(File traineeHzFile) {
        this.traineeHzFile = traineeHzFile;
    }

    public File getTraineeHzFile() {
        return traineeHzFile;
    }

    private void run() throws InterruptedException, ExecutionException {
        System.out.printf("Exercises: %s\n", workout.size());
        System.out.printf("Expected running time: %s seconds\n", workout.size() * durationSec);
        System.out.printf("Trainee's per coach: %s\n", traineeVmCount);
        System.out.printf("Trainee track logging: %s\n", traineeTrackLogging);

        long startMs = System.currentTimeMillis();

        createCoachHazelcastInstance();
        coachExecutor = coachHz.getExecutorService("Coach:Executor");

        signalHeadCoachAvailable();

        log.log(Level.INFO, format("Starting %s trainee Java Virtual Machines", traineeVmCount));
        submitToAllAndWait(coachExecutor, new SpawnTraineesTask(traineeVmCount, traineeVmOptions, traineeTrackLogging));

        long durationMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, (format("Trainee Java Virtual Machines have started after %s ms\n", durationMs)));

        for (Exercise exercise : workout.getExerciseList()) {
            run(exercise);
        }

        getTraineeHazelcastClient().getLifecycleService().shutdown();

        submitToAllAndWait(coachExecutor, new DestroyTraineesTask());

        long elapsedMs = System.currentTimeMillis() - startMs;
        System.out.printf("Total running time: %s\n", elapsedMs / 1000);
    }

    private void run(Exercise exercise) {
        try {
            log.log(Level.INFO, "Exercise initializing");
            submitToAllAndWait(traineeExecutor, new InitExerciseTask(exercise));

            log.log(Level.INFO, "Exercise global setup");
            submitToOneAndWait(new GenericExerciseTask("globalSetup"));

            log.log(Level.INFO, "Exercise local setup");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localSetup"));

            log.log(Level.INFO, "Exercise task");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("start"));

            log.log(Level.INFO, format("Exercise running for %s seconds", durationSec));
            sleepSeconds(durationSec);

            log.log(Level.INFO, "Exercise stop");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("stop"));

            log.log(Level.INFO, "Exercise global verify");
            submitToOneAndWait(new GenericExerciseTask("globalVerify"));

            log.log(Level.INFO, "Exercise local verify");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localVerify"));

            log.log(Level.INFO, "Exercise local tear down");
            submitToAllAndWait(traineeExecutor, new GenericExerciseTask("localTearDown"));

            log.log(Level.INFO, "Exercise global tear down");
            submitToOneAndWait(new GenericExerciseTask("globalTearDown"));
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed", e);
        }
    }

    private void submitToOneAndWait(Callable task) throws InterruptedException, ExecutionException {
        traineeExecutor.submit(task).get();
    }

    private void submitToAllAndWait(IExecutorService executorService, Callable task) throws InterruptedException, ExecutionException {
        Map<Member, Future> map = executorService.submitToAllMembers(task);
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
        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to run per workout)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec traineeTrackLoggingSpec = parser.accepts("traineeTrackLogging", "If the coach is tracking trainee logging");
        OptionSpec<Integer> traineeCountSpec = parser.accepts("traineeVmCount", "Number of trainee VM's per coach")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        OptionSpec<String> traineeVmOptionsSpec = parser.accepts("traineeVmOptions", "Trainee VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("");
        OptionSpec<String> traineeHzFileSpec = parser.accepts("traineeHzFile", "The Hazelcast xml configuration file for the trainee")
                .withRequiredArg().ofType(String.class);
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class);

        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();

        OptionSet options;
        try {
            options = parser.parse(args);
            if (options.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            String workoutFileName = "workout.json";
            List<String> workoutFiles = options.nonOptionArguments();
            if (workoutFiles.size() == 1) {
                workoutFileName = workoutFiles.get(0);
            } else if (workoutFiles.size() > 1) {
                exitWithError("Too many workout files specified.");
            }

            Workout workout = createWorkout(new File(workoutFileName));

            HeadCoach coach = new HeadCoach();
            coach.setWorkout(workout);
            coach.setTraineeTrackLogging(options.has(traineeTrackLoggingSpec));
            coach.setDurationSec(options.valueOf(durationSpec));
            coach.setTraineeVmOptions(options.valueOf(traineeVmOptionsSpec));
            coach.setTraineeVmCount(options.valueOf(traineeCountSpec));
            if (options.hasArgument(traineeHzFileSpec)) {
                File file = new File(options.valueOf(traineeHzFileSpec));
                if (!file.exists()) {
                    exitWithError(format("Trainee Hazelcast config file [%s] does not exist\n", file));
                }
                coach.setTraineeHzFile(file);
            }

            if (options.hasArgument(coachHzFileSpec)) {
                File file = new File(options.valueOf(coachHzFileSpec));
                if (!file.exists()) {
                    exitWithError(format("Coach Hazelcast config file [%s] does not exist\n", file));
                }
                coach.setCoachHzFile(file);
            }

            coach.run();
            System.exit(0);
        } catch (OptionException e) {
            Utils.exitWithError(e.getMessage() + ". Use --help to get overview of the help options.");
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
