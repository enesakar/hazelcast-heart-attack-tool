package com.hazelcast.heartattack;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.*;
import com.hazelcast.instance.MemberImpl;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.*;
import static java.lang.String.format;

public class Manager {

    private final static File HEART_ATTACK_HOME = getHeartAttackHome();
    private final static ILogger log = Logger.getLogger(Manager.class);

    private Workout workout;
    private File coachHzFile;
    private final List<HeartAttack> heartAttackList = Collections.synchronizedList(new LinkedList<HeartAttack>());
    private IExecutorService coachExecutor;
    private HazelcastInstance client;
    private ITopic statusTopic;
    private volatile ExerciseRecipe exerciseRecipe;
    private File traineeClassPath;
    private boolean cleanGym;

    public void setWorkout(Workout workout) {
        this.workout = workout;
    }

    public ExerciseRecipe getExerciseRecipe() {
        return exerciseRecipe;
    }

    public void setTraineeClassPath(File traineeClassPath) {
        this.traineeClassPath = traineeClassPath;
    }

    public File getTraineeClassPath() {
        return traineeClassPath;
    }

    public void setCleanGym(boolean cleanGym) {
        this.cleanGym = cleanGym;
    }

    public boolean isCleanGym() {
        return cleanGym;
    }


    public String membersString() {
        StringBuilder sb = new StringBuilder("\n\nMembers [");
        final Set<Member> members = client.getCluster().getMembers();
        sb.append(members != null ? members.size() : 0);
        sb.append("] {");
        if (members != null) {
            for (Member member : members) {
                sb.append("\n\t").append(member);
            }
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private void run() throws Exception {
        initClient();

        log.log(Level.INFO, membersString());

        if(cleanGym){
            sendStatusUpdate("Starting cleanup gyms");
            submitToAllAndWait(coachExecutor,new CleanGym());
            sendStatusUpdate("Finished cleanup gyms");
        }

        byte[] bytes = null;
        if (traineeClassPath != null) {
            bytes = Utils.zip(traineeClassPath);
        }
        submitToAllAndWait(coachExecutor, new InitWorkout(workout, bytes));

        TraineeVmSettings traineeVmSettings = workout.getTraineeVmSettings();
        Set<Member> members = client.getCluster().getMembers();
        log.log(Level.INFO, format("Trainee track logging: %s", traineeVmSettings.isTrackLogging()));
        log.log(Level.INFO, format("Trainee's per coach: %s", traineeVmSettings.getTraineeCount()));
        log.log(Level.INFO, format("Total number of coaches: %s", members.size()));
        log.log(Level.INFO, format("Total number of trainees: %s", members.size() * traineeVmSettings.getTraineeCount()));

        ITopic heartAttackTopic = client.getTopic(Coach.COACH_HEART_ATTACK_TOPIC);
        heartAttackTopic.addMessageListener(new MessageListener() {
            @Override
            public void onMessage(Message message) {
                Object messageObject = message.getMessageObject();
                if (messageObject instanceof HeartAttack) {
                    HeartAttack heartAttack = (HeartAttack) messageObject;
                    heartAttackList.add(heartAttack);
                    log.log(Level.SEVERE, "Remote machine heart attack detected:" + heartAttack);
                } else if (messageObject instanceof Exception) {
                    Exception e = (Exception) messageObject;
                    log.log(Level.SEVERE, e.getMessage(), e);
                } else {
                    log.log(Level.INFO, messageObject.toString());
                }
            }
        });

        long startMs = System.currentTimeMillis();

        runWorkout(workout);

        //the manager needs to sleep some to make sure that it will get heartattacks if they are there.
        log.log(Level.INFO, "Starting cooldown (10 sec)");
        Utils.sleepSeconds(10);
        log.log(Level.INFO, "Finished cooldown");

        client.getLifecycleService().shutdown();

        long elapsedMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, format("Total running time: %s seconds", elapsedMs / 1000));

        if (heartAttackList.isEmpty()) {
            log.log(Level.INFO, "-----------------------------------------------------------------------------");
            log.log(Level.INFO, "No heart attacks have been detected!");
            log.log(Level.INFO, "-----------------------------------------------------------------------------");
            System.exit(0);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(heartAttackList.size()).append(" Heart attacks have been detected!!!\n");
            for (HeartAttack heartAttack : heartAttackList) {
                sb.append("-----------------------------------------------------------------------------\n");
                sb.append(heartAttack).append('\n');
            }
            sb.append("-----------------------------------------------------------------------------\n");
            log.log(Level.SEVERE, sb.toString());
            System.exit(1);
        }
    }

    private void runWorkout(Workout workout) throws Exception {
        sendStatusUpdate(format("Starting workout: %s", workout.getId()));
        sendStatusUpdate(format("Exercises in workout: %s", workout.size()));
        sendStatusUpdate(format("Running time per exercise: %s seconds", workout.getDuration()));
        sendStatusUpdate(format("Expected total workout time: %s seconds", workout.size() * workout.getDuration()));

        //we need to make sure that before we start, there are no trainees running anymore.
        //log.log(Level.INFO, "Ensuring trainee all killed");
        stopTrainees();
        startTrainees(workout.getTraineeVmSettings());

        for (ExerciseRecipe exerciseRecipe : workout.getExerciseRecipeList()) {
            boolean success = run(workout, exerciseRecipe);
            if (!success && workout.isFailFast()) {
                log.log(Level.INFO, "Aborting working due to failure");
                break;
            }

            if (!success || workout.getTraineeVmSettings().isRefreshJvm()) {
                stopTrainees();
                startTrainees(workout.getTraineeVmSettings());
            }
        }

        stopTrainees();
    }

    private boolean run(Workout workout, ExerciseRecipe exerciseRecipe) {
        sendStatusUpdate(format("Running exercise : %s", exerciseRecipe.getExerciseId()));

        this.exerciseRecipe = exerciseRecipe;
        int oldCount = heartAttackList.size();
        try {
            sendStatusUpdate(exerciseRecipe.toString());

            sendStatusUpdate("Starting Exercise initialization");
            submitToAllAndWait(coachExecutor, new PrepareCoachForExercise(exerciseRecipe));
            submitToAllTrainesAndWait(new InitExercise(exerciseRecipe), "exercise initializing");
            sendStatusUpdate("Completed Exercise initialization");

            sendStatusUpdate("Starting exercise global setup");
            submitToOneTrainee(new GenericExerciseTask("globalSetup"));
            sendStatusUpdate("Completed exercise global setup");

            sendStatusUpdate("Starting exercise local setup");
            submitToAllTrainesAndWait(new GenericExerciseTask("localSetup"), "exercise local setup");
            sendStatusUpdate("Completed exercise local setup");

            sendStatusUpdate("Starting exercise start");
            submitToAllTrainesAndWait(new GenericExerciseTask("start"), "exercise start");
            sendStatusUpdate("Completed exercise start");

            sendStatusUpdate(format("Exercise running for %s seconds", workout.getDuration()));
            sleepSeconds(workout.getDuration());
            sendStatusUpdate("Exercise finished running");

            sendStatusUpdate("Starting exercise stop");
            submitToAllTrainesAndWait(new GenericExerciseTask("stop"), "exercise stop");
            sendStatusUpdate("Completed exercise stop");

            sendStatusUpdate("Starting exercise global verify");
            submitToOneTrainee(new GenericExerciseTask("globalVerify"));
            sendStatusUpdate("Completed exercise global verify");

            sendStatusUpdate("Starting exercise local verify");
            submitToAllTrainesAndWait(new GenericExerciseTask("localVerify"), "exercise local verify");
            sendStatusUpdate("Completed exercise local verify");

            sendStatusUpdate("Starting exercise local teardown");
            submitToAllTrainesAndWait(new GenericExerciseTask("localTearDown"), "exercise local tearDown");
            sendStatusUpdate("Completed exercise local teardown");

            sendStatusUpdate("Starting exercise global teardown");
            submitToOneTrainee(new GenericExerciseTask("globalTearDown"));
            sendStatusUpdate("Finished exercise global teardown");

            return heartAttackList.size() > oldCount;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed", e);
            return false;
        }
    }

    public void sleepSeconds(int seconds) {
        int period = 30;
        int big = seconds / period;
        int small = seconds % period;

        for (int k = 1; k <= big; k++) {
            if (heartAttackList.size() > 0) {
                sendStatusUpdate("Heart attack detected, aborting execution of exercise");
                return;
            }

            Utils.sleepSeconds(period);
            final int elapsed = period * k;
            final float percentage = (100f*elapsed) / seconds;
            String msg = format( "Running %s of %s seconds %-4.2f percent complete", elapsed, seconds,percentage);
            sendStatusUpdate(msg);
        }

        Utils.sleepSeconds(small);
    }

    private void stopTrainees() throws Exception {
        sendStatusUpdate("Stopping all remaining trainees");
        submitToAllAndWait(coachExecutor, new TerminateWorkout());
        sendStatusUpdate("All remaining trainees have been terminated");
    }

    private long startTrainees(TraineeVmSettings traineeVmSettings) throws Exception {
        long startMs = System.currentTimeMillis();
        final int traineeCount = traineeVmSettings.getTraineeCount();
        final int totalTraineeCount = traineeCount * client.getCluster().getMembers().size();
        log.log(Level.INFO, format("Starting a grand total of %s Trainee Java Virtual Machines", totalTraineeCount));
        submitToAllAndWait(coachExecutor, new SpawnTrainees(traineeVmSettings));
        long durationMs = System.currentTimeMillis() - startMs;
        log.log(Level.INFO, (format("Finished starting a grand total of %s Trainees after %s ms\n", totalTraineeCount, durationMs)));
        return startMs;
    }

    private void sendStatusUpdate(String s) {
        try {
            statusTopic.publish(s);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to echo to all members", e);
        }
    }

    private void submitToOneTrainee(Callable task) throws InterruptedException, ExecutionException {
        Future future = coachExecutor.submit(new TellTrainee(task));
        try {
            Object o = future.get(1000, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof HeartAttackAlreadyThrownRuntimeException)) {
                statusTopic.publish(new HeartAttack(null, null, null, null, getExerciseRecipe(), e));
            }
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            statusTopic.publish(new HeartAttack("Timeout waiting for remote operation to complete", null, null, null, getExerciseRecipe(), e));
            throw new RuntimeException(e);
        }
    }

    private void submitToAllTrainesAndWait(Callable task, String taskDescription) throws InterruptedException, ExecutionException {
        submitToAllAndWait(coachExecutor, new ShoutToTraineesTask(task, taskDescription));
    }

    private void submitToAllAndWait(IExecutorService executorService, Callable task) throws InterruptedException, ExecutionException {
        Map<Member, Future> map = executorService.submitToAllMembers(task);
        getAllFutures(map.values());
    }

    private void getAllFutures(Collection<Future> futures) throws InterruptedException, ExecutionException {
        for (Future future : futures) {
            try {
                Object o = future.get(1000, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                statusTopic.publish(new HeartAttack("Timeout waiting for remote operation to complete", null, null, null, getExerciseRecipe(), e));
                throw new RuntimeException(e);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof HeartAttackAlreadyThrownRuntimeException)) {
                    statusTopic.publish(new HeartAttack(null, null, null, null, getExerciseRecipe(), e));
                }
                throw new RuntimeException(e);
            }
        }
    }

    private void initClient() throws FileNotFoundException {
        Config coachConfig = new XmlConfigBuilder(new FileInputStream(coachHzFile)).build();

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setGroupConfig(coachConfig.getGroupConfig());
        clientConfig.addAddress("localhost:" + coachConfig.getNetworkConfig().getPort());
        client = HazelcastClient.newHazelcastClient(clientConfig);
        coachExecutor = client.getExecutorService("Coach:Executor");
        statusTopic = client.getTopic(Coach.COACH_HEART_ATTACK_TOPIC);
    }

    public static void main(String[] args) throws Exception {
        log.log(Level.INFO, "Hazelcast Heart Attack Manager");
        log.log(Level.INFO, format("Version: %s", getVersion()));
        log.log(Level.INFO, format("HEART_ATTACK_HOME: %s", HEART_ATTACK_HOME));

        OptionParser parser = new OptionParser();
        OptionSpec cleanGymSpec = parser.accepts("cleanGym", "Cleans the gym directory on all coaches");

        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to run per workout)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec traineeTrackLoggingSpec = parser.accepts("traineeTrackLogging", "If the coach is tracking trainee logging");
        OptionSpec<Integer> traineeCountSpec = parser.accepts("traineeVmCount", "Number of trainee VM's per coach")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        OptionSpec<String> traineeClassPathSpec = parser.accepts("traineeClassPath", "A directory containing the jars that are going to be uploaded to the coaches")
                .withRequiredArg().ofType(String.class);

        OptionSpec<Integer> traineeStartupTimeoutSpec = parser.accepts("traineeStartupTimeout", "The startup timeout in seconds for a trainee")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec<Boolean> traineeRefreshSpec = parser.accepts("traineeFresh", "If the trainee VM's should be replaced after every workout")
                .withRequiredArg().ofType(Boolean.class).defaultsTo(false);
        OptionSpec<Boolean> failFastSpec = parser.accepts("failFast", "It the workout should fail immediately when an exercise from a workout fails instead of continuing ")
                .withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        OptionSpec<String> traineeVmOptionsSpec = parser.accepts("traineeVmOptions", "Trainee VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("");
        OptionSpec<String> traineeHzFileSpec = parser.accepts("traineeHzFile", "The Hazelcast xml configuration file for the trainee")
                .withRequiredArg().ofType(String.class).defaultsTo(HEART_ATTACK_HOME + File.separator + "conf" + File.separator + "trainee-hazelcast.xml");
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class).defaultsTo(HEART_ATTACK_HOME + File.separator + "conf" + File.separator + "coach-hazelcast.xml");

        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();

        OptionSet options;
        Manager manager = new Manager();

        try {
            options = parser.parse(args);

            if (options.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            manager.setCleanGym(options.has(cleanGymSpec));

            if (options.has(traineeClassPathSpec)) {
                File traineeClassPath = new File(options.valueOf(traineeClassPathSpec));
                if (!traineeClassPath.exists()) {
                    exitWithError(format("traineeClassPath [%s] doesn't exist.\n", traineeClassPath));
                }
                manager.setTraineeClassPath(traineeClassPath);
            }

            File coachHzFile = new File(options.valueOf(coachHzFileSpec));
            if (!coachHzFile.exists()) {
                exitWithError(format("Coach Hazelcast config file [%s] does not exist.\n", coachHzFile));
            }
            manager.coachHzFile = coachHzFile;

            String workoutFileName = "workout.properties";
            List<String> workoutFiles = options.nonOptionArguments();
            if (workoutFiles.size() == 1) {
                workoutFileName = workoutFiles.get(0);
            } else if (workoutFiles.size() > 1) {
                exitWithError("Too many workout files specified.");
            }

            Workout workout = createWorkout(new File(workoutFileName));

            manager.setWorkout(workout);
            workout.setDuration(options.valueOf(durationSpec));
            workout.setFailFast(options.valueOf(failFastSpec));

            File traineeHzFile = new File(options.valueOf(traineeHzFileSpec));
            if (!traineeHzFile.exists()) {
                exitWithError(format("Trainee Hazelcast config file [%s] does not exist.\n", traineeHzFile));
            }

            TraineeVmSettings traineeVmSettings = new TraineeVmSettings();
            traineeVmSettings.setTrackLogging(options.has(traineeTrackLoggingSpec));
            traineeVmSettings.setVmOptions(options.valueOf(traineeVmOptionsSpec));
            traineeVmSettings.setTraineeCount(options.valueOf(traineeCountSpec));
            traineeVmSettings.setTraineeStartupTimeout(options.valueOf(traineeStartupTimeoutSpec));
            traineeVmSettings.setHzConfig(Utils.asText(traineeHzFile));
            traineeVmSettings.setRefreshJvm(options.valueOf(traineeRefreshSpec));
            workout.setTraineeVmSettings(traineeVmSettings);
        } catch (OptionException e) {
            Utils.exitWithError(e.getMessage() + ". Use --help to get overview of the help options.");
        }

        try {
            manager.run();
            System.exit(0);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to run workout", e);
            System.exit(1);
        }
    }


    private static Workout createWorkout(File file) throws Exception {
        Properties properties = loadProperties(file);

        Map<String, ExerciseRecipe> recipies = new HashMap<String, ExerciseRecipe>();
        for (String property : properties.stringPropertyNames()) {
            String value = (String) properties.get(property);
            int indexOfDot = property.indexOf(".");

            String recipeId = "";
            String field = property;
            if (indexOfDot > -1) {
                recipeId = property.substring(0, indexOfDot);
                field = property.substring(indexOfDot + 1);
            }

            ExerciseRecipe recipe = recipies.get(recipeId);
            if (recipe == null) {
                recipe = new ExerciseRecipe();
                recipies.put(recipeId, recipe);
            }

            recipe.setProperty(field, value);
        }

        List<String> recipeIds = new LinkedList<String>(recipies.keySet());
        Collections.sort(recipeIds);

        Workout workout = new Workout();
        for (String recipeId : recipeIds) {
            ExerciseRecipe recipe = recipies.get(recipeId);
            if (recipe.getClassname() == null) {
                if ("".equals(recipeId)) {
                    throw new RuntimeException(format("There is no class set for the in property file [%s]." +
                            "Add class=YourExerciseClass",
                            file.getAbsolutePath()));
                } else {
                    throw new RuntimeException(format("There is no class set for exercise [%s] in property file [%s]." +
                            "Add %s.class=YourExerciseClass",
                            recipeId, file.getAbsolutePath(), recipeId));
                }
            }
            workout.addExercise(recipe);
        }
        return workout;
    }

    private static Properties loadProperties(File file) {
        Properties properties = new Properties();
        final FileInputStream in;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            //should not be thrown since it is already verified that the property file exist.
            throw new RuntimeException(e);
        }
        try {
            properties.load(in);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(format("Failed to load workout property file [%s]", file.getAbsolutePath()), e);
        } finally {
            Utils.closeQuietly(in);
        }
    }


}
