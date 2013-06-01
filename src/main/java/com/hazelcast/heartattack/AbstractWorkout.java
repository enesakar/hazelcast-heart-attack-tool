package com.hazelcast.heartattack;

import com.hazelcast.core.HazelcastInstance;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public abstract class AbstractWorkout<F extends WorkoutFactory> implements Workout {

    protected int memberIndex;
    protected int memberCount;
    protected HazelcastInstance hazelcastInstance;
    protected F factory;

    protected volatile boolean stop = false;
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final Set<Thread> threads = new HashSet<Thread>();

    public HazelcastInstance getHazelcastInstance() {
        return hazelcastInstance;
    }

    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public int getMemberIndex() {
        return memberIndex;
    }

    public void setMemberIndex(int memberIndex) {
        this.memberIndex = memberIndex;
    }

    public F getFactory() {
        return factory;
    }

    public void setFactory(F factory) {
        this.factory = factory;
    }

    public Thread spawn(Runnable runnable) {
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
                System.out.println(Thread.currentThread().getName()+" Waiting");
                startLatch.await();
                System.out.println(Thread.currentThread().getName()+" Starting");
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
