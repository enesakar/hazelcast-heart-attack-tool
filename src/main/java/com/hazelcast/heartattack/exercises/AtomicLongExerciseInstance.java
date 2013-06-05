package com.hazelcast.heartattack.exercises;


import com.hazelcast.core.IAtomicLong;
import com.hazelcast.heartattack.AbstractExerciseInstance;

import java.util.Random;
import java.util.logging.Logger;

public class AtomicLongExerciseInstance extends AbstractExerciseInstance<AtomicLongExercise> {

    private final static Logger log = Logger.getLogger(AtomicLongExerciseInstance.class.getName());

    private IAtomicLong totalCounter;
    private IAtomicLong[] counters;

    @Override
    public void localSetup() {
        totalCounter = hazelcastInstance.getAtomicLong("AtomicLongExercise-totalCounter");
        counters = new IAtomicLong[exercise.getCountersLength()];
        for (int k = 0; k < counters.length; k++) {
            counters[k] = hazelcastInstance.getAtomicLong("AtomicLongExercise-counter-" + k);
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
    public void localTearDown() {
        totalCounter.destroy();
        totalCounter = null;

        for (IAtomicLong counter : counters) {
            counter.destroy();
        }
        counters = null;
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
                    System.out.println(Thread.currentThread().getName() + " At iteration: " + iteration);
                }
                iteration++;
            }

            totalCounter.addAndGet(iteration);
        }
    }
}

