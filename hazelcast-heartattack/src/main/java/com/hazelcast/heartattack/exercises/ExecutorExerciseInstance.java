package com.hazelcast.heartattack.exercises;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.heartattack.AbstractExerciseInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ExecutorExerciseInstance extends AbstractExerciseInstance<ExecutorExercise> {

    final static ILogger log = Logger.getLogger(AtomicLongExerciseInstance.class.getName());

    private IExecutorService[] executors;
    private IAtomicLong executedCounter;
    private IAtomicLong expectedExecutedCounter;

    @Override
    public void localSetup() {
        log.log(Level.INFO, "localSetup");

        executors = new IExecutorService[exercise.executorCount];
        for (int k = 0; k < executors.length; k++) {
            executors[k] = getHazelcastInstance().getExecutorService(getExercise().getId() + ":Executor-" + k);
        }

        for (int k = 0; k < exercise.threadCount; k++) {
            spawn(new Worker());
        }

        executedCounter = hazelcastInstance.getAtomicLong(getExercise().getId() + ":ExecutedCounter");
        expectedExecutedCounter = hazelcastInstance.getAtomicLong(getExercise().getId() + ":ExpectedExecutedCounter");
    }

    @Override
    public void globalTearDown() throws Exception {
        executedCounter.destroy();
        expectedExecutedCounter.destroy();
        for (IExecutorService executor : executors) {
            executor.shutdownNow();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                throw new RuntimeException("Time out while waiting for executor shutdown");
            }
            executor.destroy();
        }
    }

    @Override
    public void globalVerify() throws Exception {
        long actualCount = executedCounter.get();
        long expectedCount = expectedExecutedCounter.get();
        if (actualCount != expectedCount) {
            throw new RuntimeException("ActualCount:" + actualCount + " doesn't match ExpectedCount:" + expectedCount);
        }
    }

    private class Worker implements Runnable {
        private final Random random = new Random();

        @Override
        public void run() {
            long iteration = 0;
            while (!stop) {
                int index = random.nextInt(executors.length);
                IExecutorService executorService = executors[index];

                Future future = executorService.submit(new Task());
                try {
                    future.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException();
                } catch (ExecutionException e) {
                    throw new RuntimeException();
                }

                if (iteration % 10000 == 0) {
                    log.log(Level.INFO, Thread.currentThread().getName() + " At iteration: " + iteration);
                }
                iteration++;
            }

            expectedExecutedCounter.addAndGet(iteration);
        }
    }

    private static class Task implements Runnable, Serializable, HazelcastInstanceAware {
        private transient HazelcastInstance hz;

        @Override
        public void run() {
            ExecutorExerciseInstance exerciseInstance = (ExecutorExerciseInstance) hz.getUserContext().get(EXERCISE_INSTANCE);
            exerciseInstance.executedCounter.incrementAndGet();
        }

        @Override
        public void setHazelcastInstance(HazelcastInstance hz) {
            this.hz = hz;
        }
    }
}
