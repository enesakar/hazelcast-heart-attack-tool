package com.hazelcast.heartattack.exercises;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AtomicLongExercise extends AbstractExercise {

    @JsonProperty
    private int countersLength = 1000;
    @JsonProperty
    private int threadCount = 1;

    @JsonProperty
    private String clazz = null;


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
