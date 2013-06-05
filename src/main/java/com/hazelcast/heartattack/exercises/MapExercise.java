package com.hazelcast.heartattack.exercises;

public class MapExercise extends AbstractExercise {
    private int threadCount = 10;
    private int keyLength = 10;
    private int valueLength = 10;
    private int keyCount = 10000;
    private int valueCount = 10000;

    public MapExercise() {
        super(MapExerciseInstance.class);
    }

    public int getKeyCount() {
        return keyCount;
    }

    public void setKeyCount(int keyCount) {
        this.keyCount = keyCount;
    }

    public int getValueCount() {
        return valueCount;
    }

    public void setValueCount(int valueCount) {
        this.valueCount = valueCount;
    }

    public int getKeyLength() {
        return keyLength;
    }

    public void setKeyLength(int keyLength) {
        this.keyLength = keyLength;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public int getValueLength() {
        return valueLength;
    }

    public void setValueLength(int valueLength) {
        this.valueLength = valueLength;
    }
}
