package com.hazelcast.heartattack;

import com.hazelcast.core.HazelcastInstance;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public abstract class AbstractExerciseInstance<E extends Exercise> implements ExerciseInstance {

    protected HazelcastInstance hazelcastInstance;
    protected E exercise;

    protected volatile boolean stop = false;
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final Set<Thread> threads = new HashSet<Thread>();

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    public E getExercise() {
        return exercise;
    }

    public void setExercise(E exercise) {
        this.exercise = exercise;
    }

    @Override
    public void globalSetup() throws Exception {
    }

    @Override
    public void localSetup() throws Exception {
    }

    @Override
    public void localTearDown() throws Exception {
    }

    @Override
    public void globalTearDown() throws Exception {
    }

    @Override
    public void globalVerify() throws Exception {
    }

    @Override
    public void localVerify() throws Exception {
    }

    public final Thread spawn(Runnable runnable) {
        Thread thread = new Thread(new CatchingRunnable(runnable));
        threads.add(thread);
        thread.start();
        return thread;
    }

    private class CatchingRunnable implements Runnable {
        private final Runnable runnable;

        private CatchingRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void run() {
            try {
                System.out.println(Thread.currentThread().getName() + " Waiting");
                startLatch.await();
                System.out.println(Thread.currentThread().getName() + " Starting");
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    @Override
    public void start() {
        System.out.println("Start called");
        startLatch.countDown();
    }

    @Override
    public void stop() throws InterruptedException {
        stop = true;
        for (Thread thread : threads) {
            thread.join();
        }
        threads.clear();
    }
}
