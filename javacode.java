
ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();

builder.setStatusLevel(Level.DEBUG);
builder.setConfigurationName("DefaultLogger");

// get-config-builder => config-builder

// create a console appender
AppenderComponentBuilder appenderBuilder = builder.newAppender("Console", "CONSOLE").addAttribute("target",
                                                                                                  ConsoleAppender.Target.SYSTEM_OUT);
appenderBuilder.add(builder.newLayout("PatternLayout")
                    .addAttribute("pattern", pattern));

// get-console-appender-builder! => appender-builder


RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.DEBUG);
rootLogger.add(builder.newAppenderRef("Console"));

builder.add(appenderBuilder);

// create a rolling file appender
LayoutComponentBuilder layoutBuilder = builder.newLayout("PatternLayout")
                .addAttribute("pattern", pattern);
        ComponentBuilder triggeringPolicy = builder.newComponent("Policies")
                .addComponent(builder.newComponent("SizeBasedTriggeringPolicy").addAttribute("size", "1KB"));
        appenderBuilder = builder.newAppender("LogToRollingFile", "RollingFile")
                .addAttribute("fileName", fileName)
                .addAttribute("filePattern", fileName+"-%d{MM-dd-yy-HH-mm-ss}.log.")
                .add(layoutBuilder)
                .addComponent(triggeringPolicy);
        builder.add(appenderBuilder);
        rootLogger.add(builder.newAppenderRef("LogToRollingFile"));
        builder.add(rootLogger);
 Configurator.reconfigure(builder.build());


// En dan Baeldung code op: https://www.baeldung.com/log4j2-programmatic-config

<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.11.0</version>
    </dependency>
    <dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j-impl</artifactId>
    <version>2.11.0</version>
</dependency>

// [2022-01-09 19:31] slf4j hopelijk niet nodig.
    AppenderComponentBuilder console
    = builder.newAppender("stdout", "Console");

builder.add(console);

AppenderComponentBuilder file
    = builder.newAppender("log", "File");
file.addAttribute("fileName", "target/logging.log");

builder.add(file);


AppenderComponentBuilder rollingFile
    = builder.newAppender("rolling", "RollingFile");
rollingFile.addAttribute("fileName", "rolling.log");
rollingFile.addAttribute("filePattern", "rolling-%d{MM-dd-yy}.log.gz");

builder.add(rollingFile);

// Configuring Filters -> nog even niet.


// Layouts wel:
LayoutComponentBuilder standard
    = builder.newLayout("PatternLayout");
standard.addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable");

console.add(standard);
file.add(standard);
rolling.add(standard);

// 3.4. Configuring the Root Logger


RootLoggerComponentBuilder rootLogger
    = builder.newRootLogger(Level.ERROR);
rootLogger.add(builder.newAppenderRef("stdout"));

builder.add(rootLogger);

// To point our logger at a specific appender, we don't give it an
// instance of the builder. Instead, we refer to it by the name that
// we gave it earlier.

3.5. Configuring Additional Loggers

    Child loggers can be used to target specific packages or logger names.

    Let's add a logger for the com package in our application, setting the logging level to DEBUG and having those go to our log appender:

    LoggerComponentBuilder logger = builder.newLogger("com", Level.DEBUG);
logger.add(builder.newAppenderRef("log"));
logger.addAttribute("additivity", false);

builder.add(logger);

Note that we can set additivity with our loggers, which indicates whether this logger should inherit properties like logging level and appender types from its ancestors.

    // 3.6. Configuring Other Components -> nog even niet, trigger for rollingfileappender.

    //  Configurator.initialize(builder.build());

    // geen calls verder over hoe het aan te roepen.
