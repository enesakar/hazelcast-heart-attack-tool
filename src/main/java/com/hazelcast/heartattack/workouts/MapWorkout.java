package com.hazelcast.heartattack.workouts;

import com.hazelcast.core.IMap;
import com.hazelcast.heartattack.AbstractWorkout;
import org.apache.log4j.Logger;

import java.util.Random;

public class MapWorkout extends AbstractWorkout<MapWorkoutFactory> {

    private final static Logger log = Logger.getLogger(MapWorkout.class);


    private final static String alphabet = "abcdefghijklmnopqrstuvwxyz1234567890";

    private IMap<Object, Object> map;
    private String[] keys;
    private String[] values;
    private Random random = new Random();

    @Override
    public void setUp() throws Exception {
        map = hazelcastInstance.getMap("MapWorkout:map");
        for (int k = 0; k < factory.getThreadCount(); k++) {
            spawn(new Worker());
        }

        keys = new String[factory.getKeyCount()];
        for (int k = 0; k < keys.length; k++) {
            keys[k] = makeString(factory.getKeyLength());
        }

        values = new String[factory.getValueCount()];
        for (int k = 0; k < values.length; k++) {
            values[k] = makeString(factory.getValueLength());
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
    public void tearDown() throws Exception {
        map.destroy();
        map = null;
    }

    @Override
    public void verifyNoHeartAttack() throws Exception {
        //To change body of implemented methods use File | Settings | File Templates.
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
                    System.out.println(Thread.currentThread().getName() + " At iteration: " + iteration);
                }
                iteration++;
            }
        }
    }
}
