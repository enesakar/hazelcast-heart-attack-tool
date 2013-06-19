package com.hazelcast.heartattack;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.*;
import static java.lang.String.format;

public class HeadCoach extends Coach {
    final static ILogger log = Logger.getLogger(HeadCoach.class.getName());

    private IExecutorService coachExecutor;
    private int durationSec = 60;
    private Workout workout;
    private TraineeSettings traineeSettings = new TraineeSettings();
    private Boolean failFast = true;
    private List<HeartAttack> heartAttacks = Collections.synchronizedList(new LinkedList<HeartAttack>());

    public void setWorkout(Workout workout) {
        this.workout = workout;
    }

    public HazelcastInstance getCoachHazelcastInstance() {
        return coachHz;
    }

    public void setDurationSec(Integer durationSec) {
        this.durationSec = durationSec;
    }

    public int getDurationSec() {
        return durationSec;
    }

    public TraineeSettings getTraineeSettings() {
        return traineeSettings;
    }

    public void setTraineeSettings(TraineeSettings traineeSettings) {
        this.traineeSettings = traineeSettings;
    }

    public void setFailFast(Boolean failFast) {
        this.failFast = failFast;
    }

    public Boolean getFailFast() {
        return failFast;
    }

    private void run() throws Exception {
        log.log(Level.INFO, format("Trainee track logging: %s", traineeSettings.isTrackLogging()));
        log.log(Level.INFO, format("Trainee's per coach: %s", traineeSettings.getTraineeCount()));

        initCoachHazelcastInstance();

        Set<Member> members = coachHz.getCluster().getMembers();
        log.log(Level.INFO, format("Total number of coaches: %s", members.size()));
        log.log(Level.INFO, format("Total number of trainees: %s", members.size() * traineeSettings.getTraineeCount()));

        new Thread() {
            public void run() {
                for (; ; ) {
                    try {
                        final HeartAttack heartAttack = heartAttackQueue.take();
                        log.log(Level.SEVERE, heartAttack.toString());
                        heartAttacks.add(heartAttack);
                    } catch (Exception e) {
                    }
                }
            }
        }.start();

        coachExecutor = coachHz.getExecutorService("Coach:Executor");

        long startMs = System.currentTimeMillis();

        runWorkout();

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, format("Total running time: %s seconds", elapsedMs / 1000));

        if (heartAttacks.isEmpty()) {
            log.log(Level.INFO, "No heart attacks have been detected!");
            System.exit(0);
        } else {
            log.log(Level.SEVERE, "Heart attacks have been detected!");
            for (HeartAttack heartAttack : heartAttacks) {
                log.log(Level.SEVERE, format("\t%s", heartAttack));
            }
            System.exit(1);
        }
    }

    private void runWorkout() throws Exception {
        log.log(Level.INFO, format("Exercises in workout: %s", workout.size()));
        log.log(Level.INFO, format("Running time per exercise: %s seconds", durationSec));
        log.log(Level.INFO, format("Expected total workout time: %s seconds", workout.size() * durationSec));


        //we need to make sure that before we start, there are no trainees running anymore.
        //log.log(Level.INFO, "Ensuring trainee all killed");
        //stopTrainees();
        startTrainees();

        for (Exercise exercise : workout.getExerciseList()) {
            boolean success = run(exercise);
            if (!success && failFast) {
                log.log(Level.INFO, "Aborting working due to failure");
                break;
            }

            if (!success || traineeSettings.isRefreshJvm()) {
                stopTrainees();
                startTrainees();
            }
        }

        stopTrainees();
    }

    private void stopTrainees() throws Exception {
        submitToAllAndWait(coachExecutor, new DestroyTraineesTask());
    }

    private long startTrainees() throws Exception {
        long startMs = System.currentTimeMillis();
        log.log(Level.INFO, format("Starting %s trainee Java Virtual Machines", traineeSettings.getTraineeCount()));
        submitToAllAndWait(coachExecutor, new SpawnTraineesTask(traineeSettings));
        long durationMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, (format("Trainee Java Virtual Machines have started after %s ms\n", durationMs)));
        return startMs;
    }

    private boolean run(Exercise exercise) {
        try {
            log.log(Level.INFO, exercise.getDescription());

            log.log(Level.INFO, "Exercise initializing");
            submitToAllAndWait(coachExecutor, new CoachInitExerciceTask(exercise));
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
            return false;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed", e);
            return true;
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

      public static void main(String[] args) throws Exception {

        log.log(Level.INFO, "Hazelcast Heart Attack Coach");
        log.log(Level.INFO, format("Version: %s", getVersion()));
        log.log(Level.INFO, format("HEART_ATTACK_HOME: %s", heartAttackHome));

        OptionParser parser = new OptionParser();
        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to run per workout)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec traineeTrackLoggingSpec = parser.accepts("traineeTrackLogging", "If the coach is tracking trainee logging");
        OptionSpec<Integer> traineeCountSpec = parser.accepts("traineeVmCount", "Number of trainee VM's per coach")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        OptionSpec<Boolean> traineeRefreshSpec = parser.accepts("traineeFresh", "If the trainee VM's should be replaced after every workout")
                .withRequiredArg().ofType(Boolean.class).defaultsTo(false);
        OptionSpec<Boolean> failFastSpec = parser.accepts("failFast", "It the workout should fail immediately when an exercise from a workout fails instead of continuing ")
                .withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        OptionSpec<String> traineeVmOptionsSpec = parser.accepts("traineeVmOptions", "Trainee VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("-Xmx128m -Dhazelcast.logging.type=log4j  -Dlog4j.configuration=file:" + heartAttackHome + File.separator + "conf" + File.separator + "trainee-log4j.xml");
        OptionSpec<String> traineeHzFileSpec = parser.accepts("traineeHzFile", "The Hazelcast xml configuration file for the trainee")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome + File.separator + "conf" + File.separator + "trainee-hazelcast.xml");
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome + File.separator + "conf" + File.separator + "coach-hazelcast.xml");

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
            coach.setDurationSec(options.valueOf(durationSpec));
            coach.setFailFast(options.valueOf(failFastSpec));

            File traineeHzFile = new File(options.valueOf(traineeHzFileSpec));
            if (!traineeHzFile.exists()) {
                exitWithError(format("Trainee Hazelcast config file [%s] does not exist.\n", traineeHzFile));
            }

            TraineeSettings traineeSettings = coach.getTraineeSettings();
            traineeSettings.setTrackLogging(options.has(traineeTrackLoggingSpec));
            traineeSettings.setVmOptions(options.valueOf(traineeVmOptionsSpec));
            traineeSettings.setTraineeCount(options.valueOf(traineeCountSpec));
            traineeSettings.setHzConfig(Utils.asText(traineeHzFile));
            traineeSettings.setRefreshJvm(options.valueOf(traineeRefreshSpec));

            File coachHzFile = new File(options.valueOf(coachHzFileSpec));
            if (!coachHzFile.exists()) {
                exitWithError(format("Coach Hazelcast config file [%s] does not exist.\n", coachHzFile));
            }
            coach.setCoachHzFile(coachHzFile);

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
