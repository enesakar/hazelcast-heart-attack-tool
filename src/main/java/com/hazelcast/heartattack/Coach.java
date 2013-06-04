package com.hazelcast.heartattack;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.tasks.*;
import com.hazelcast.heartattack.workouts.MapWorkoutFactory;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static com.hazelcast.heartattack.Utils.sleepSeconds;

public class Coach {

    private final static Logger log = Logger.getLogger(Coach.class.getName());

    private final HazelcastInstance coachHz;
    private final HazelcastInstance traineeHz;
    private final IExecutorService traineeExecutor;
    private final IExecutorService coachExecutor;
    private boolean isHeadCoach;
    private int durationSec;
    private String clientVmOptions;

    public Coach() throws ExecutionException, InterruptedException {
        this.coachHz = createCoachHazelcastInstance();
        this.coachExecutor = coachHz.getExecutorService("Coach:Executor");

        this.traineeHz = Trainee.createHazelcastInstance();
        this.traineeExecutor = traineeHz.getExecutorService(Trainee.TRAINEE_EXECUTOR);
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

    public void setClientVmOptions(String clientVmOptions) {
        this.clientVmOptions = clientVmOptions;
    }

    public String getClientVmOptions() {
        return clientVmOptions;
    }

    private HazelcastInstance createCoachHazelcastInstance() {
        Config config = new Config();
        config.getUserContext().put("Coach", this);
        config.getGroupConfig().setName("coach");
        return Hazelcast.newHazelcastInstance(config);
    }

    private void run() throws InterruptedException, ExecutionException {
        if (!isHeadCoach) {
            System.out.println("Starting assistant coach");
            awaitHeadCoachAvailable();
        } else {
            System.out.println("Starting head coach");
            System.out.println("clientVmOptions: " + clientVmOptions);

            MapWorkoutFactory factory = new MapWorkoutFactory();
            factory.setThreadCount(2);
            factory.setKeyLength(100);
            factory.setValueLength(100);
            factory.setKeyCount(100);
            factory.setValueCount(100);

            signalHeadCoachAvailable();

            log.info("Starting trainee Java Virtual Machines");
            submitToAllAndWait(coachExecutor, new SpawnTrainees(1, clientVmOptions));
            log.info("All trainee Java Virtual Machines have started");

            submitToAllAndWait(traineeExecutor, new InitWorkoutTask(factory));

            submitToOneAndWait(new GenericWorkoutTask("globalSetup"));

            submitToAllAndWait(traineeExecutor, new GenericWorkoutTask("localSetup"));

            submitToAllAndWait(traineeExecutor, new GenericWorkoutTask("start"));

            sleepSeconds(durationSec);

            submitToAllAndWait(traineeExecutor, new GenericWorkoutTask("stop"));

            submitToAllAndWait(traineeExecutor, new GenericWorkoutTask("localVerify"));

            submitToOneAndWait(new GenericWorkoutTask("globalVerify"));

            submitToAllAndWait(traineeExecutor, new GenericWorkoutTask("localTearDown"));

            submitToOneAndWait(new GenericWorkoutTask("globalTearDown"));

            submitToAllAndWait(traineeExecutor, new ShutdownTask());
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
        OptionSpec<Integer> durationSpec = parser.accepts("duration", "Number of seconds to run (defaults to 60 seconds)")
                .withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec<String> clientVmOptionsSpec = parser.accepts("clientVmOptions", "Client VM options (quotes can be used)")
                .withRequiredArg().ofType(String.class).defaultsTo("");
        OptionSpec isHeadCoachSpec =
                parser.accepts("headCoach", "If the node is a head coach");
        OptionSpec helpSpec =
                parser.accepts("help", "Show help").forHelp();

        OptionSet set;
        try {
            set = parser.parse(args);
            Coach main = new Coach();
            main.setIsHeadCoach(set.has(isHeadCoachSpec));
            main.setDurationSec(set.valueOf(durationSpec));
            main.setClientVmOptions(set.valueOf(clientVmOptionsSpec));

            main.run();
        } catch (OptionException e) {
            System.out.println(e.getMessage() + ". Use --help to get overview of the help options.");
            System.exit(1);
        }
    }
}
