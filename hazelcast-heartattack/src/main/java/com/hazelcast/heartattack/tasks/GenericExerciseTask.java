package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.ExerciseInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.logging.Level;

import static java.lang.String.format;

public class GenericExerciseTask implements Callable, Serializable, HazelcastInstanceAware {

    final static ILogger log = Logger.getLogger(GenericExerciseTask.class.getName());

    private transient HazelcastInstance hz;
    private final String methodName;

    public GenericExerciseTask(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.log(Level.INFO, "Calling exerciseInstance." + methodName + "()");

            ExerciseInstance exerciseInstance = (ExerciseInstance) hz.getUserContext().get(ExerciseInstance.EXERCISE_INSTANCE);
            if (exerciseInstance == null) {
                throw new IllegalStateException("No ExerciseInstance found");
            }

            Method method = exerciseInstance.getClass().getMethod(methodName);
            method.invoke(exerciseInstance);
            log.log(Level.INFO, "Finished calling exerciseInstance." + methodName + "()");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            log.log(Level.SEVERE, format("Failed to execute exercise.%s()", methodName), e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}