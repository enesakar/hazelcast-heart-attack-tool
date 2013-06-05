package com.hazelcast.heartattack;

import com.hazelcast.core.HazelcastInstance;

import java.io.Serializable;

/**
 * Responsible for creating a Exercise.
 *
 * The idea behind the Exercise is that when the heart attack tool starts, it sends a Exercise to each member
 * and therefor the Exercise must be serializable. When the Exercise is received by the node, it calls
 * {@link #newInstance(com.hazelcast.core.HazelcastInstance)} to create the real ExerciseInstance (which is not serializable).
 *
 * If a Exercise needs arguments, these arguments should be added on top of the Exercise, and the Exercise can then be passed
 * to the ExerciseInstance. See the {@link com.hazelcast.heartattack.exercises.AtomicLongExerciseInstance} for an example.
 *
 * @author Peter Veentjer.
 */
public interface Exercise extends Serializable {

    ExerciseInstance newInstance(HazelcastInstance hz);
}
