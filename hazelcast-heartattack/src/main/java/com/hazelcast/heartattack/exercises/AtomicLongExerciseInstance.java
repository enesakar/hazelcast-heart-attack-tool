package com.hazelcast.heartattack.exercises;


import com.hazelcast.core.IAtomicLong;
import com.hazelcast.heartattack.AbstractExerciseInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.util.Random;
import java.util.logging.Level;

public class AtomicLongExerciseInstance extends AbstractExerciseInstance<AtomicLongExercise> {

    private final static ILogger log = Logger.getLogger(AtomicLongExerciseInstance.class);

    private IAtomicLong totalCounter;
    private IAtomicLong[] counters;

    @Override
    public void localSetup() {
        totalCounter = hazelcastInstance.getAtomicLong(getExercise().getId() + ":TotalCounter");
        counters = new IAtomicLong[exercise.getCountersLength()];
        for (int k = 0; k < counters.length; k++) {
            counters[k] = hazelcastInstance.getAtomicLong(getExercise().getId() + ":Counter-" + k);
        }

        for (int k = 0; k < exercise.getThreadCount(); k++) {
            spawn(new Worker());
        }
    }

    @Override
    public void globalVerify() {
        long expectedCount = totalCounter.get();
        long count = 0;
        for (IAtomicLong counter : counters) {
            count += counter.get();
        }

        if (expectedCount != count) {
            throw new RuntimeException("Expected count: " + expectedCount + " but found count was: " + count);
        }
    }

    @Override
    public void globalTearDown() throws Exception {
        for (IAtomicLong counter : counters) {
            counter.destroy();
        }
        totalCounter.destroy();
    }

    private class Worker implements Runnable {
        private final Random random = new Random();

        @Override
        public void run() {
            long iteration = 0;
            while (!stop) {
                int index = random.nextInt(counters.length);
                counters[index].incrementAndGet();
                if (iteration % 10000 == 0) {
                    log.log(Level.INFO, Thread.currentThread().getName() + " At iteration: " + iteration);
                }
                iteration++;
            }

            totalCounter.addAndGet(iteration);
        }
    }
}

