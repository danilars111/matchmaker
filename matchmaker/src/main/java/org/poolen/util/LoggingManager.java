package org.poolen.util;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Optional;

/**
 * Manages the application's logging setup.
 * This class is responsible for setting the log file location and name for each session,
 * and for cleaning up old log files to prevent clutter.
 */
public final class LoggingManager {

    private static final int MAX_LOG_FILES = 5;
    private static final String EXTERNAL_CONFIG_FILENAME = "logback.xml";
    private static final String INTERNAL_CONFIG_PATH = "/logging/logback.xml";


    private LoggingManager() {
        // This class should not be instantiated.
    }

    public enum LogLevel {
        ALL, TRACE, DEBUG, INFO, WARN, ERROR, OFF
    }

    public enum LogbackLogger {
        POOLEN("org.poolen"),
        SPRINGFRAMEWORK("org.springframework"),
        HIBERNATE("org.hibernate");

        private final String loggerName;

        LogbackLogger(String loggerName) {
            this.loggerName = loggerName;
        }

        public String getLoggerName() {
            return loggerName;
        }
    }

    /**
     * Initializes the logging system.
     * It determines the log path, creates a unique filename for the session,
     * manually configures Logback (preferring an external file), and cleans up old logs.
     */
    public static void initialize() {
        // These properties need to be set before we configure Logback so the config file can use them.
        Path logPath = setupLogPath();
        String timestamp = createTimestamp();

        // Now, configure the logging system using our new, clever logic.
        configureLogback();

        if (logPath != null) {
            cleanupOldLogs(logPath);
        }

        final var logger = LoggerFactory.getLogger(LoggingManager.class);
        logger.info("Logging initialized. Log file for this session: matchmaker-{}.log", timestamp);
    }

    private static Path setupLogPath() {
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir != null) {
            Path logPath = appDataDir.resolve("logs");
            System.setProperty("app.log.path", logPath.toString());
            return logPath;
        }
        System.err.println("Warning: Could not determine app data directory. Logs may not be written to file.");
        return null;
    }

    private static String createTimestamp() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        System.setProperty("app.log.timestamp", timestamp);
        return timestamp;
    }

    /**
     * Programmatically configures Logback. It first looks for an external configuration file
     * in the application's data directory. If not found, it falls back to the default
     * configuration file bundled within the application resources.
     */
    private static void configureLogback() {
        Path externalConfigPath = getLogbackConfigPath();

        // If the external file doesn't exist, we should create it from our template.
        if (externalConfigPath != null && Files.notExists(externalConfigPath)) {
            System.out.println("INFO: No external logging configuration found. Creating default in app data directory.");
            try (InputStream templateStream = LoggingManager.class.getResourceAsStream(INTERNAL_CONFIG_PATH)) {
                if (templateStream == null) {
                    System.err.println("FATAL: Default logback.xml template not found in resources!");
                    return;
                }
                Files.copy(templateStream, externalConfigPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("FATAL: Could not create external logback.xml file.");
                e.printStackTrace();
                return; // Stop here if we can't create the config.
            }
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset(); // Clear any previous configuration

            if (externalConfigPath != null && Files.exists(externalConfigPath)) {
                System.out.println("INFO: Found external logging configuration at " + externalConfigPath);
                try (InputStream configStream = new FileInputStream(externalConfigPath.toFile())) {
                    configurator.doConfigure(configStream);
                } catch (IOException e) {
                    System.err.println("ERROR: Could not read external logback config file. Falling back to default.");
                    e.printStackTrace();
                    // If reading the external file fails, fall back to the internal one.
                    configurator.doConfigure(LoggingManager.class.getResourceAsStream(INTERNAL_CONFIG_PATH));
                }
            } else {
                // This part should now be less likely to be hit, but it's a good fallback.
                System.out.println("INFO: No external logging configuration found. Using default from resources.");
                configurator.doConfigure(LoggingManager.class.getResourceAsStream(INTERNAL_CONFIG_PATH));
            }
        } catch (JoranException e) {
            // This is for printing errors during the configuration process itself.
            System.err.println("FATAL: A critical error occurred during Logback configuration.");
            e.printStackTrace();
        }
    }

    /**
     * Sets the logging level for a specific logger in the external logback.xml file.
     * This method will find and update the logger entry, or create it if it doesn't exist.
     * Afterwards, it reloads the Logback configuration to apply the change immediately.
     *
     * @param logger The logger to configure.
     * @param level  The desired logging level.
     */
    public static void setLoggerLevel(LogbackLogger logger, LogLevel level) {
        Path configPath = getLogbackConfigPath();
        if (configPath == null || Files.notExists(configPath)) {
            System.err.println("Cannot set logger level, config file not found at: " + configPath);
            return;
        }

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(configPath.toFile());

            NodeList loggerNodes = doc.getElementsByTagName("logger");
            Element loggerElement = null;
            boolean found = false;

            // Look for an existing logger element for our target
            for (int i = 0; i < loggerNodes.getLength(); i++) {
                Element existingElement = (Element) loggerNodes.item(i);
                if (logger.getLoggerName().equals(existingElement.getAttribute("name"))) {
                    loggerElement = existingElement;
                    found = true;
                    break;
                }
            }

            if (found) {
                // It exists, so we just update the level
                loggerElement.setAttribute("level", level.name());
            } else {
                // It doesn't exist, so we create a new one
                Element root = doc.getDocumentElement();
                loggerElement = doc.createElement("logger");
                loggerElement.setAttribute("name", logger.getLoggerName());
                loggerElement.setAttribute("level", level.name());
                root.appendChild(loggerElement);
            }

            // Before saving, let's clean up the document to prevent those ghastly extra blank lines.
            // This new magic removes any text nodes that only contain whitespace.
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            NodeList emptyTextNodes = (NodeList) xpath.evaluate(
                    "//text()[normalize-space(.) = '']",
                    doc,
                    XPathConstants.NODESET
            );

            for (int i = 0; i < emptyTextNodes.getLength(); i++) {
                Node emptyTextNode = emptyTextNodes.item(i);
                emptyTextNode.getParentNode().removeChild(emptyTextNode);
            }

            // This is where we'll save our beautifully structured XML file.
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();

            // --- Let's teach the transformer some manners! ---
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4"); // Indent by 4 spaces, so it's easy to read.

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(configPath.toFile());
            transformer.transform(source, result);

            // After saving, we tell Logback to reload the new configuration.
            reloadLogbackConfiguration();

        } catch (Exception e) {
            System.err.println("Oh no, something went wrong updating the logback config!");
            e.printStackTrace();
        }
    }

    /**
     * Reads the current logging level for a specific logger from the external config file.
     * @param logger The logger to check.
     * @return An Optional containing the LogLevel if found, otherwise an empty Optional.
     */
    public static Optional<LogLevel> getLoggerLevel(LogbackLogger logger) {
        Path configPath = getLogbackConfigPath();
        if (configPath == null || Files.notExists(configPath)) {
            return Optional.empty();
        }

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.parse(configPath.toFile());

            NodeList loggerNodes = doc.getElementsByTagName("logger");
            for (int i = 0; i < loggerNodes.getLength(); i++) {
                Element element = (Element) loggerNodes.item(i);
                if (logger.getLoggerName().equals(element.getAttribute("name"))) {
                    String levelString = element.getAttribute("level").toUpperCase();
                    try {
                        return Optional.of(LogLevel.valueOf(levelString));
                    } catch (IllegalArgumentException e) {
                        // The level in the file is something weird we don't understand.
                        return Optional.empty();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Oh dear, could not read the logger level from the config file.");
            e.printStackTrace();
        }
        return Optional.empty();
    }


    /**
     * A helper to get the full path to where our logback.xml file should live.
     * @return The Path to the logback.xml file in the AppData directory.
     */
    public static Path getLogbackConfigPath() {
        Path appDataDir = AppDataHandler.getAppDataDirectory();
        if (appDataDir == null) {
            System.err.println("Warning: Could not determine app data directory. Logback configuration may not be available.");
            return null;
        }
        return appDataDir.resolve(EXTERNAL_CONFIG_FILENAME);
    }

    /**
     * Forces Logback to reload its configuration from the external file.
     * This is useful after we've programmatically made changes to it.
     */
    private static void reloadLogbackConfiguration() {
        Path configPath = getLogbackConfigPath();
        if (configPath == null || Files.notExists(configPath)) {
            System.err.println("Cannot reload Logback config, file not found.");
            return;
        }

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            context.reset(); // Clear previous configuration
            configurator.doConfigure(configPath.toFile());
            System.out.println("INFO: Logback configuration reloaded.");
        } catch (JoranException e) {
            System.err.println("Oh heavens, failed to reload Logback configuration!");
            e.printStackTrace();
        }
    }


    private static void cleanupOldLogs(Path logDirectory) {
        if (Files.notExists(logDirectory)) {
            return;
        }

        try {
            Files.list(logDirectory)
                    .filter(p -> p.toString().endsWith(".log"))
                    .sorted(Comparator.comparing(LoggingManager::getLastModifiedTime).reversed())
                    .skip(MAX_LOG_FILES)
                    .forEach(LoggingManager::deleteFile);
        } catch (IOException e) {
            System.err.println("Oh dear, failed to clean up old log files: " + e.getMessage());
        }
    }

    private static FileTime getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException e) {
            return FileTime.fromMillis(0);
        }
    }

    private static void deleteFile(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            System.err.println("Couldn't delete old log file: " + path + " - " + e.getMessage());
        }
    }
}

