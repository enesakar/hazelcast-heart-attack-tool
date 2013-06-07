package com.hazelcast.heartattack;

import com.hazelcast.config.Config;
import com.hazelcast.core.*;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

public class AssistantCoach implements Coach {
    private final static Logger log = Logger.getLogger(HeadCoach.class.getName());

    private HazelcastInstance coachHz;
    private HazelcastInstance traineeHz;

    private HazelcastInstance createCoachHazelcastInstance() {
        Config config = new Config();
        config.getUserContext().put("Coach", this);
        config.getGroupConfig().setName("coach");
        return Hazelcast.newHazelcastInstance(config);
    }

    private void start() throws InterruptedException, ExecutionException {
        coachHz = createCoachHazelcastInstance();
        traineeHz = Trainee.createHazelcastInstance();
        awaitHeadCoachAvailable();
    }

    @Override
    public HazelcastInstance getTraineeHazelcastInstance() {
        return traineeHz;
    }

    private void awaitHeadCoachAvailable() {
        log.info("Awaiting Head Coach");

        ILock lock = coachHz.getLock("Coach:headCoachLock");
        lock.lock();
        ICondition condition = lock.newCondition("Coach:headCoachCondition");
        IAtomicLong available = coachHz.getAtomicLong("Coach:headCoachCount");

        try {
            while (available.get() == 0) {
                condition.await();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException();
        } finally {
            lock.unlock();
        }

        log.info("Head Coach Arrived");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Assistent Coach");

        OptionParser parser = new OptionParser();
        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();

        OptionSet set;
        try {
            set = parser.parse(args);
            if (set.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            AssistantCoach coach = new AssistantCoach();
            coach.start();
        } catch (OptionException e) {
            System.out.println(e.getMessage() + ". Use --help to get overview of the help options.");
            System.exit(1);
        }
    }
}
