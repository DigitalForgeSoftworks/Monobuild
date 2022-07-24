package org.digitalforge.monobuild.logging.internal;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class InternalLogging {

    private static final String PACKAGE = InternalLogging.class.getPackageName();
    private static final Map<String,Logger> LOGGERS = new HashMap<>();

    public static Logger getLogger(String name) {
        if (LOGGERS.containsKey(name)) {
            return LOGGERS.get(name);
        } else {
            Logger logger = LoggerFactory.getLogger(String.format("%s.%s", PACKAGE, name));
            LOGGERS.put(name, logger);
            return logger;
        }
    }

}
