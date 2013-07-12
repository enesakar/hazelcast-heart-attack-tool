package com.hazelcast.heartattack;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

public class Workout implements Serializable{

    private static final long serialVersionUID = 1;

    private List<Exercise> exerciseList = new LinkedList<Exercise>();
    private int duration;
    private TraineeSettings traineeSettings;
    private boolean failFast;

    public boolean isFailFast() {
        return failFast;
    }

    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    public TraineeSettings getTraineeSettings() {
        return traineeSettings;
    }

    public void setTraineeSettings(TraineeSettings traineeSettings) {
        this.traineeSettings = traineeSettings;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

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
