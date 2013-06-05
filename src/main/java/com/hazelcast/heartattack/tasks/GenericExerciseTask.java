package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.ExerciseInstance;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

public class GenericExerciseTask implements Callable, Serializable, HazelcastInstanceAware {

    private final static Logger log = Logger.getLogger(GenericExerciseTask.class.getName());

    private transient HazelcastInstance hz;
    private final String methodName;

    public GenericExerciseTask(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.info("Calling exercise." + methodName + "()");

            ExerciseInstance exerciseInstance = (ExerciseInstance) hz.getUserContext().get("exercise");
            if (exerciseInstance != null) {
                Method method = exerciseInstance.getClass().getMethod(methodName);
                method.invoke(exerciseInstance);
            } else {
                System.out.println("No Exercise Found for method: " + methodName);
            }

            log.info("Finished calling exercise." + methodName + "()");
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