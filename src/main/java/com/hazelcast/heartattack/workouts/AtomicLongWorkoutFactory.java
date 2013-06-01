package com.hazelcast.heartattack.workouts;

public class AtomicLongWorkoutFactory extends AbstractWorkoutFactory {

    private int countersLength = 1000;
    private int threadCount = 1;

    public AtomicLongWorkoutFactory() {
        super(AtomicLongWorkout.class);
    }

    public void setCountersLength(int countersLength) {
        this.countersLength = countersLength;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getCountersLength() {
        return countersLength;
    }

    public int getThreadCount() {
        return threadCount;
    }
}
