package com.hazelcast.heartattack.exercises;

import com.hazelcast.core.IMap;
import com.hazelcast.heartattack.AbstractExerciseInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.util.Random;
import java.util.logging.Level;

public class MapExerciseInstance extends AbstractExerciseInstance<MapExercise> {

    final static ILogger log = Logger.getLogger(MapExerciseInstance.class.getName());

    private final static String alphabet = "abcdefghijklmnopqrstuvwxyz1234567890";

    private IMap<Object, Object> map;
    private String[] keys;
    private String[] values;
    private Random random = new Random();

    @Override
    public void localSetup() throws Exception {
        map = hazelcastInstance.getMap(getExercise().getId() + ":Map");
        for (int k = 0; k < exercise.getThreadCount(); k++) {
            spawn(new Worker());
        }

        keys = new String[exercise.getKeyCount()];
        for (int k = 0; k < keys.length; k++) {
            keys[k] = makeString(exercise.getKeyLength());
        }

        values = new String[exercise.getValueCount()];
        for (int k = 0; k < values.length; k++) {
            values[k] = makeString(exercise.getValueLength());
        }
    }

    private String makeString(int length) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < length; k++) {
            char c = alphabet.charAt(random.nextInt(alphabet.length()));
            sb.append(c);
        }

        return sb.toString();
    }

    @Override
    public void globalTearDown() throws Exception {
        map.destroy();
    }

    private class Worker implements Runnable {
        private final Random random = new Random();

        @Override
        public void run() {
            long iteration = 0;
            while (!stop) {
                map.put(System.nanoTime(), System.nanoTime());
                Object key = keys[random.nextInt(keys.length)];
                Object value = values[random.nextInt(values.length)];
                map.put(key, value);
                if (iteration % 10000 == 0) {
                    log.log(Level.INFO, Thread.currentThread().getName() + " At iteration: " + iteration);
                }
                iteration++;
            }
        }
    }
}
