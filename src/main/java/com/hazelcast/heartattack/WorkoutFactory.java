package com.hazelcast.heartattack;

import com.hazelcast.core.HazelcastInstance;

import java.io.Serializable;

/**
 * Responsible for creating a WorkOut.
 *
 * The idea behind the WorkOutFactory is that when the heart attack tool starts, it sends a WorkoutFactory to each member
 * and therefor the WorkoutFactory must be serializable. When the WorkoutFactory is received by the node, it calls
 * {@link #newWorkout(com.hazelcast.core.HazelcastInstance)} to create the real workout (which is not serializable).
 *
 * If a Workout needs arguments, these arguments should be added on top of the Factory, and the Factory can then be passed
 * to the Workout. See the {@link com.hazelcast.heartattack.workouts.AtomicLongWorkout} for an example.
 *
 * @author Peter Veentjer.
 */
public interface WorkoutFactory extends Serializable {

    Workout newWorkout(HazelcastInstance hz);
}
