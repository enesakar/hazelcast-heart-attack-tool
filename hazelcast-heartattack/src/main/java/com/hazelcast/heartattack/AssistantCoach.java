package com.hazelcast.heartattack;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.File;

import static com.hazelcast.heartattack.Utils.exitWithError;
import static com.hazelcast.heartattack.Utils.getVersion;
import static java.lang.String.format;

public class AssistantCoach extends Coach {

    final static ILogger log = Logger.getLogger(AssistantCoach.class.getName());

    private void run() throws Exception {
        initCoachHazelcastInstance();
        System.out.println("Hazelcast Assistant Coach is Ready for action");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Hazelcast Assistant Coach");
        System.out.printf("Version: %s\n", getVersion());
        System.out.printf("HEART_ATTACK_HOME: %s\n", heartAttackHome);

        OptionParser parser = new OptionParser();
        OptionSpec helpSpec = parser.accepts("help", "Show help").forHelp();
        OptionSpec<String> coachHzFileSpec = parser.accepts("coachHzFile", "The Hazelcast xml configuration file for the coach")
                .withRequiredArg().ofType(String.class).defaultsTo(heartAttackHome + File.separator + "conf" + File.separator + "coach-hazelcast.xml");

        try {
            OptionSet options = parser.parse(args);

            if (options.has(helpSpec)) {
                parser.printHelpOn(System.out);
                System.exit(0);
            }

            AssistantCoach coach = new AssistantCoach();
            File coachHzFile = new File(options.valueOf(coachHzFileSpec));
            if (!coachHzFile.exists()) {
                exitWithError(format("Coach Hazelcast config file [%s] does not exist\n", coachHzFile));
            }
            coach.setCoachHzFile(coachHzFile);
            coach.run();
        } catch (OptionException e) {
            exitWithError(e.getMessage() + "\nUse --help to get overview of the help options.");
        }
    }
}
