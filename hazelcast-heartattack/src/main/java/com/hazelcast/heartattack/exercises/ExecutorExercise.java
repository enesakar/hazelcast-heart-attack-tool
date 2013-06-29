package com.hazelcast.heartattack.exercises;

import com.hazelcast.heartattack.AbstractExercise;

/**
 * The ExecutorExercise starts one or more executors and sends runnables to these executors. These runnables increment
 * a local counter. By verifying the number of runnables send and the total number of increments done, one can see if
 * the exercise was correct.
 */
public class ExecutorExercise extends AbstractExercise {

    public int executorCount = 1;

    //the number of threads submitting tasks to the executor.
    public int threadCount = 5;

    public ExecutorExercise() {
        super(ExecutorExerciseInstance.class);
    }
}
