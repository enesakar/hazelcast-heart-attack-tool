package com.hazelcast.heartattack.exercises;

import com.hazelcast.heartattack.AbstractExercise;

public class ExecutorExercise extends AbstractExercise {

    public int executorCount = 1;

    //the number of threads submitting tasks to the executor.
    public int threadCount = 5;

    public ExecutorExercise() {
        super(ExecutorExerciseInstance.class);
    }
}
