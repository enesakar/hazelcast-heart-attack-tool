package com.hazelcast.heartattack;

import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class Workout implements Serializable{

    private static final long serialVersionUID = 1;

    private final String id = UUID.randomUUID().toString();

    private List<ExerciseRecipe> exerciseRecipeList = new LinkedList<ExerciseRecipe>();
    private int duration;
    private TraineeSettings traineeSettings;
    private boolean failFast;

    public String getId() {
        return id;
    }

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

    public void addExercise(ExerciseRecipe exerciseRecipe) {
        exerciseRecipeList.add(exerciseRecipe);
    }

    public List<ExerciseRecipe> getExerciseRecipeList() {
        return exerciseRecipeList;
    }

    public int size() {
        return exerciseRecipeList.size();
    }

    @Override
    public String toString() {
        return "Workout{" +
                "duration=" + duration +
                ", id='" + id + '\'' +
                ", exerciseList=" + exerciseRecipeList +
                ", traineeSettings=" + traineeSettings +
                ", failFast=" + failFast +
                '}';
    }
}
