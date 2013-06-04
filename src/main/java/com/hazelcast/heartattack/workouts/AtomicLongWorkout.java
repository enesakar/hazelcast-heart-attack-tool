package com.hazelcast.heartattack.workouts;


import com.hazelcast.core.IAtomicLong;
import com.hazelcast.heartattack.AbstractWorkout;

import java.util.Random;
import java.util.logging.Logger;

public class AtomicLongWorkout extends AbstractWorkout<AtomicLongWorkoutFactory> {

    private final static Logger log = Logger.getLogger(AtomicLongWorkout.class.getName());

    private IAtomicLong totalCounter;
    private IAtomicLong[] counters;

    @Override
    public void setUp() {
        totalCounter = hazelcastInstance.getAtomicLong("AtomicLongWorkout-totalCounter");
        counters = new IAtomicLong[factory.getCountersLength()];
        for (int k = 0; k < counters.length; k++) {
            counters[k] = hazelcastInstance.getAtomicLong("AtomicLongWorkout-counter-" + k);
        }

        for (int k = 0; k < factory.getThreadCount(); k++) {
            spawn(new Worker());
        }
    }

    @Override
    public void verifyNoHeartAttack() {
        if (memberIndex == 0) {
            long expectedCount = totalCounter.get();
            long count = 0;
            for (int k = 0; k < counters.length; k++) {
                count += counters[k].get();
            }

            if (expectedCount != count) {
                throw new RuntimeException("Expected count: " + expectedCount + " but found count was: " + count);
            }
        }
    }

    @Override
    public void tearDown() {
        totalCounter.destroy();
        totalCounter = null;

        for (int k = 0; k < counters.length; k++) {
            counters[k].destroy();
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

