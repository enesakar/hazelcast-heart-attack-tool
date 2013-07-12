package com.hazelcast.heartattack;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.WorkoutTask;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.Future;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.*;
import static java.lang.String.format;

public class Manager {

    public final static File heartAttackHome = getHeartAttackHome();

    private final List<HeartAttack> heartAttackList = Collections.synchronizedList(new LinkedList<HeartAttack>());

    final static ILogger log = Logger.getLogger(Manager.class.getName());

    private Workout workout;
    private File coachHzFile;

    public void setWorkout(Workout workout) {
        this.workout = workout;
    }

    private void run() throws Exception {
        Config coachConfig = new XmlConfigBuilder(new FileInputStream(coachHzFile)).build();

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.setGroupConfig(coachConfig.getGroupConfig());
        clientConfig.addAddress("localhost:" + coachConfig.getNetworkConfig().getPort());
        HazelcastInstance client = HazelcastClient.newHazelcastClient(clientConfig);

        TraineeSettings traineeSettings = workout.getTraineeSettings();
        Set<Member> members = client.getCluster().getMembers();
        log.log(Level.INFO, format("Trainee track logging: %s", traineeSettings.isTrackLogging()));
        log.log(Level.INFO, format("Trainee's per coach: %s", traineeSettings.getTraineeCount()));
        log.log(Level.INFO, format("Total number of coaches: %s", members.size()));
        log.log(Level.INFO, format("Total number of trainees: %s", members.size() * traineeSettings.getTraineeCount()));

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

        Future future = client.getExecutorService("Coach:Executor").submit(new WorkoutTask(workout));
        future.get();

        //the manager needs to sleep some to make sure that it will get heartattacks if they are there.
        log.log(Level.INFO, "Starting cooldown (10 sec)");
        sleepSeconds(10);
        log.log(Level.INFO, "Finished cooldown");

        long elapsedMs = System.currentTimeMillis() - startMs;

        client.getLifecycleService().shutdown();

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
        log.log(Level.INFO, format("Total running time: %s seconds", elapsedMs / 1000));
    }



    public static void main(String[] args) throws Exception {
        log.log(Level.INFO, "Hazelcast Heart Attack Manager");
        log.log(Level.INFO, format("Version: %s", getVersion()));
        log.log(Level.INFO, format("HEART_ATTACK_HOME: %s", heartAttackHome));

        OptionParser parser = new OptionParser();
        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to run per workout)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec traineeTrackLoggingSpec = parser.accepts("traineeTrackLogging", "If the coach is tracking trainee logging");
        OptionSpec<Integer> traineeCountSpec = parser.accepts("traineeVmCount", "Number of trainee VM's per coach")
                .withRequiredArg().ofType(Integer.class).defaultsTo(1);
        OptionSpec<Integer> traineeStartupTimeoutSpec = parser.accepts("traineeStartupTimeout", "The startup timeout in seconds for a trainee")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec<Boolean> traineeRefreshSpec = parser.accepts("traineeFresh", "If the trainee VM's should be replaced after every workout")
                .withRequiredArg().ofType(Boolean.class).defaultsTo(false);
        OptionSpec<Boolean> failFastSpec = parser.accepts("failFast", "It the workout should fail immediately when an exercise from a workout fails instead of continuing ")
                .withRequiredArg().ofType(Boolean.class).defaultsTo(true);
        OptionSpec<String> traineeVmOptionsSpec = parser.accepts("traineeVmOptions", "Trainee VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("");
        OptionSpec<String> traineeHzFileSpec = parser.accepts("traineeHzFile", "The Hazelcast xml configuration file for the trainee")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome + File.separator + "conf" + File.separator + "trainee-hazelcast.xml");
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome + File.separator + "conf" + File.separator + "coach-hazelcast.xml");

        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();

        OptionSet options;
        Manager coach = new Manager();

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

            coach.setWorkout(workout);
            workout.setDuration(options.valueOf(durationSpec));
            workout.setFailFast(options.valueOf(failFastSpec));

            File traineeHzFile = new File(options.valueOf(traineeHzFileSpec));
            if (!traineeHzFile.exists()) {
                exitWithError(format("Trainee Hazelcast config file [%s] does not exist.\n", traineeHzFile));
            }

            TraineeSettings traineeSettings = new TraineeSettings();
            traineeSettings.setTrackLogging(options.has(traineeTrackLoggingSpec));
            traineeSettings.setVmOptions(options.valueOf(traineeVmOptionsSpec));
            traineeSettings.setTraineeCount(options.valueOf(traineeCountSpec));
            traineeSettings.setTraineeStartupTimeout(options.valueOf(traineeStartupTimeoutSpec));
            traineeSettings.setHzConfig(Utils.asText(traineeHzFile));
            traineeSettings.setRefreshJvm(options.valueOf(traineeRefreshSpec));
            workout.setTraineeSettings(traineeSettings);

            File coachHzFile = new File(options.valueOf(coachHzFileSpec));
            if (!coachHzFile.exists()) {
                exitWithError(format("Coach Hazelcast config file [%s] does not exist.\n", coachHzFile));
            }
            coach.coachHzFile = coachHzFile;

        } catch (OptionException e) {
            Utils.exitWithError(e.getMessage() + ". Use --help to get overview of the help options.");
        }

        try {
            coach.run();
            System.exit(0);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Failed to run workout", e);
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
