package com.hazelcast.heartattack.tasks;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.heartattack.Workout;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

public class GenericWorkoutTask implements Callable, Serializable, HazelcastInstanceAware {

    private final static Logger log = Logger.getLogger(GenericWorkoutTask.class.getName());

    private transient HazelcastInstance hz;
    private final String methodName;

    public GenericWorkoutTask(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public Object call() throws Exception {
        try {
            log.info("Calling workout." + methodName + "()");

            Workout workout = (Workout) hz.getUserContext().get("workout");
            if (workout != null) {
                Method method = workout.getClass().getMethod(methodName);
                method.invoke(workout);
            } else {
                System.out.println("No Workout Found for method: " + methodName);
            }

            log.info("Finished calling workout." + methodName + "()");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            log.log(Level.SEVERE, format("Failed to execute workout.%s()", methodName), e);
            throw e;
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hz) {
        this.hz = hz;
    }
}