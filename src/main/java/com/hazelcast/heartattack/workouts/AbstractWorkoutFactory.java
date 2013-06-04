package com.hazelcast.heartattack.workouts;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.heartattack.AbstractWorkout;
import com.hazelcast.heartattack.Workout;
import com.hazelcast.heartattack.WorkoutFactory;

public abstract class AbstractWorkoutFactory implements WorkoutFactory {

    private final String clazzName;

    public AbstractWorkoutFactory(Class<? extends AbstractWorkout> clazz) {
        clazzName = clazz.getName();
    }

    @Override
    public Workout newWorkout(HazelcastInstance hz) {
        try {
            Class<AbstractWorkout> clazz = (Class<AbstractWorkout>) AbstractWorkoutFactory.class.getClassLoader().loadClass(clazzName);
            AbstractWorkout workout = clazz.newInstance();
            workout.setFactory(this);
            workout.setHazelcastInstance(hz);
            return workout;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
