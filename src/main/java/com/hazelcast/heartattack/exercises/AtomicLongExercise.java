package com.hazelcast.heartattack.exercises;

public class AtomicLongExercise extends AbstractExercise {

    private int countersLength = 1000;
    private int threadCount = 1;

    public AtomicLongExercise() {
        super(AtomicLongExerciseInstance.class);
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
