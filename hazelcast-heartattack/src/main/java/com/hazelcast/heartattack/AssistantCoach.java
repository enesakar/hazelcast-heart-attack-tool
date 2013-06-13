package com.hazelcast.heartattack;

import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.ICondition;
import com.hazelcast.core.ILock;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

import static com.hazelcast.heartattack.Utils.exitWithError;
import static java.lang.String.format;

public class AssistantCoach extends Coach {
    final static ILogger log = Logger.getLogger(AssistantCoach.class.getName());

    private void run() throws InterruptedException, ExecutionException {
        createCoachHazelcastInstance();
        traineeHz = Trainee.createHazelcastInstance();
        awaitHeadCoachAvailable();
    }

    private void awaitHeadCoachAvailable() {
        log.log(Level.INFO, "Awaiting Head Coach");

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

        log.log(Level.INFO,"Head Coach Arrived");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Assistant Coach");

        OptionParser parser = new OptionParser();
        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class);

        OptionSet options;
        try {
            options = parser.parse(args);

            if (options.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            AssistantCoach coach = new AssistantCoach();
            if (options.hasArgument(coachHzFileSpec)) {
                File file = new File(options.valueOf(coachHzFileSpec));
                if (!file.exists()) {
                    exitWithError(format("Coach Hazelcast config file [%s] does not exist\n", file));
                }
                coach.setCoachHzFile(file);
            }

            coach.run();
        } catch (OptionException e) {
            exitWithError(e.getMessage() + "\nUse --help to get overview of the help options.");
        }
    }
}
