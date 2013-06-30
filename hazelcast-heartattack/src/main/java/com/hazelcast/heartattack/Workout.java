package com.hazelcast.heartattack;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class Workout implements Serializable{

    private static final long serialVersionUID = 1;

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

    @Override
    public String toString() {
        return "Workout{" +
                "exerciseList=" + exerciseList +
                '}';
    }
}
