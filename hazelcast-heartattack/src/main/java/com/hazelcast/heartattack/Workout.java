package com.hazelcast.heartattack;

import java.util.LinkedList;
import java.util.List;

public class Workout {

    private List<Exercise> exerciseList = new LinkedList<Exercise>();

    public void addExercise(Exercise exercise) {
        exerciseList.add(exercise);
    }

    public List<Exercise> getExerciseList() {
        return exerciseList;
    }

    public int size() {
        return exerciseList.size();
    }
}
