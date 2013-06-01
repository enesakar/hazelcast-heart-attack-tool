package com.hazelcast.heartattack;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import com.hazelcast.heartattack.workouts.MapWorkoutFactory;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class Coach {

    private final HazelcastInstance coachHazelcastInstance;
    private final HazelcastInstance traineeHazelcastInstance;
    private final IExecutorService traineeExecutor;
    private final IExecutorService coachExecutor;
    private final boolean isHeadCoach;
    private final int durationSec;
    private final IMap<Object, Object> traineeParticipantMap;

    public Coach(boolean isHeadCoach, int durationSec) throws ExecutionException, InterruptedException {
        this.coachHazelcastInstance = createCoachHazelcastInstance();
        this.coachExecutor = coachHazelcastInstance.getExecutorService("Coach:Executor");

        this.traineeHazelcastInstance = createTraineeHazelcastInstance();
        this.traineeExecutor = traineeHazelcastInstance.getExecutorService(Trainee.TRAINEE_EXECUTOR);
        this.traineeParticipantMap = traineeHazelcastInstance.getMap(Trainee.TRAINEE_PARTICIPANT_MAP);

        this.isHeadCoach = isHeadCoach;
        this.durationSec = durationSec;
    }

    private HazelcastInstance createCoachHazelcastInstance() {
        Config config = new Config();
        config.getUserContext().put("Coach", this);
        config.getGroupConfig().setName("coach");
        return Hazelcast.newHazelcastInstance(config);
    }

    private HazelcastInstance createTraineeHazelcastInstance() {
        Config config = new Config();
        config.getGroupConfig().setName(Trainee.TRAINEE_GROUP);
        return Hazelcast.newHazelcastInstance(config);
    }

    private void run() throws InterruptedException, ExecutionException {
        if (!isHeadCoach) {
            awaitHeadCoachAvailable();
        } else {
            signalHeadCoachAvailable();

            submitToAllAndWait(coachExecutor, new SpawnTrainees(1));

            Set<Future> futures = new HashSet<Future>();
            int k = 0;
            final Set<Member> members = traineeHazelcastInstance.getCluster().getMembers();
            for (Member member : members) {
                //AtomicLongWorkoutFactory atomicLongHeartAttackFactory = new AtomicLongWorkoutFactory();
                //atomicLongHeartAttackFactory.setCountersLength(10000);
                //atomicLongHeartAttackFactory.setThreadCount(1);

                MapWorkoutFactory factory = new MapWorkoutFactory();
                factory.setThreadCount(2);
                factory.setKeyLength(100);
                factory.setValueLength(100);
                factory.setKeyCount(100);
                factory.setValueCount(100);

                SetupWorkoutTask task = new SetupWorkoutTask(members.size(), k, factory);
                futures.add(traineeExecutor.submitToMember(task, member));
                k++;
            }
            getAllFutures(futures);

            submitToAllAndWait(traineeExecutor, new StartTask());

            sleep(durationSec);

            submitToAllAndWait(traineeExecutor, new StopTask());

            submitToAllAndWait(traineeExecutor, new VerifyTask());

            submitToAllAndWait(traineeExecutor, new TeardownTask());

            submitToAllAndWait(traineeExecutor, new ShutdownTask());
        }
    }


    private static void sleep(int seconds) {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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

        ILock lock = coachHazelcastInstance.getLock("Coach:headCoachLock");
        lock.lock();
        ICondition condition = lock.newCondition("Coach:headCoachCondition");
        IAtomicLong available = coachHazelcastInstance.getAtomicLong("Coach:headCoachCount");

        try {
            available.incrementAndGet();
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void awaitHeadCoachAvailable() {
        System.out.println("Awaiting Head Coach");

        ILock lock = coachHazelcastInstance.getLock("Coach:headCoachLock");
        lock.lock();
        ICondition condition = lock.newCondition("Coach:headCoachCondition");
        IAtomicLong available = coachHazelcastInstance.getAtomicLong("Coach:headCoachCount");

        try {
            while (available.get() == 0) {
                condition.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            lock.unlock();
        }

        System.out.println("Head Couch Arrived");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Heart Attack Coach");

        OptionParser parser = new OptionParser();
        OptionSpec<Integer> duration =
                parser.accepts("duration", "Number of seconds to run (defaults to 60 seconds)").withRequiredArg().ofType(Integer.class).defaultsTo(60);
        OptionSpec isHeadCoachOption =
                parser.accepts("headCoach", "If the node is a head coach");
        OptionSpec help =
                parser.accepts("h", "Show help").forHelp();

        OptionSet set;
        try {
            set = parser.parse(args);

            boolean isHeadCoach = set.has(isHeadCoachOption);

            System.out.println("Is head coach: " + isHeadCoach);

            int durationSeconds = set.valueOf(duration);

            System.out.println("Duration in seconds: " + durationSeconds);
            Coach main = new Coach(isHeadCoach, durationSeconds);
            main.run();
        } catch (OptionException e) {
            System.out.println(e.getMessage() + ". Use -h to get overview of the help options.");
            System.exit(1);
        }
    }

    static class SetupWorkoutTask implements Callable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;
        private final int memberCount;
        private final int memberIndex;
        private final WorkoutFactory factory;

        SetupWorkoutTask(int memberCount, int memberIndex, WorkoutFactory factory) {
            this.memberCount = memberCount;
            this.memberIndex = memberIndex;
            this.factory = factory;
        }

        @Override
        public Object call() throws Exception {
            try {
                System.out.println("Init Workout");
                Workout workout = factory.newWorkout(hz, memberIndex, memberCount);
                hz.getUserContext().put("workout", workout);
                workout.setUp();
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }

    static class StartTask implements Callable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;

        @Override
        public Object call() throws Exception {
            try {
                System.out.println("Start Workout");

                Workout workout = (Workout) hz.getUserContext().get("workout");
                if (workout != null) {
                    workout.start();
                } else {
                    System.out.println("No Workout Found to Start");
                }
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }

    static class StopTask implements Callable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;

        @Override
        public Object call() throws Exception {
            System.out.println("Stop Workout");

            Workout workout = (Workout) hz.getUserContext().get("workout");
            if (workout != null) {
                workout.stop();
            }
            return null;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }

    static class TeardownTask implements Callable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;

        @Override
        public Object call() throws Exception {
            System.out.println("Teardown Workout");

            Workout workout = (Workout) hz.getUserContext().get("workout");
            if (workout != null) {
                workout.tearDown();
            }
            return null;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }

    static class VerifyTask implements Callable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;

        @Override
        public Object call() throws Exception {
            System.out.println("Verify Workout");

            Workout workout = (Workout) hz.getUserContext().get("workout");
            if (workout != null) {
                workout.verifyNoHeartAttack();
            }
            return null;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }

    static class SpawnTrainees implements Callable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;
        private final int count;


        SpawnTrainees(int count) {
            this.count = count;
        }

        @Override
        public Object call() throws Exception {
            Coach coach = (Coach) hz.getUserContext().get("Coach");

            String classpath = System.getProperty("java.class.path");

            for (int k = 0; k < count; k++) {
                String id = UUID.randomUUID().toString();

                Process process = new ProcessBuilder("java", "-cp", classpath, Trainee.class.getName(), id)
                        .directory(new File(System.getProperty("user.dir")))
                        .start();
                new LoggingThread(id, process.getInputStream()).start();
                new LoggingThread(id, process.getErrorStream()).start();

                boolean found = false;
                for (int l = 0; l < 30; l++) {
                    if (coach.traineeParticipantMap.containsKey(id)) {
                        coach.traineeParticipantMap.remove(id);
                        found = true;
                    } else {
                        sleep(1);
                    }
                }

                if (!found) {
                    throw new RuntimeException();
                } else {
                    System.out.println("Trainee: " + id + " Started");
                }

                //int exitCode = process.waitFor();
                //if (exitCode != 0) {
                //    throw new RuntimeException("Failed to spawn a new vm");
                //}
            }
            return null;
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }

    private static class LoggingThread extends Thread {
        private final InputStream inputStream;
        private final String prefix;

        public LoggingThread(String prefix, InputStream inputStream) {
            this.inputStream = inputStream;
            this.prefix = prefix;
        }

        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                for (; ; ) {
                    final String line = br.readLine();
                    if (line == null) break;
                    System.out.println(prefix + ": " + line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class ShutdownTask implements Callable, Serializable {
        @Override
        public Object call() throws Exception {
            System.out.println("Shutdown");
            System.exit(0);
            return null;
        }
    }
}
