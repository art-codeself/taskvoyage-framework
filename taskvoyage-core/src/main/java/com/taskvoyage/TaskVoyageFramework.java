package com.taskvoyage;

/**
 * TaskVoyage Framework - Core Module
 * <p>
 * This module provides the core TaskVoyage task execution engine.
 * No @SpringBootApplication here — this is a library, not a runnable application.
 * <p>
 * Auto-configuration is handled by Spring Boot's {@code AutoConfiguration.imports} mechanism:
 * <ul>
 *   <li>{@link com.taskvoyage.config.TaskVoyageAutoConfiguration} - common beans (ObjectMapper, RetryPolicy)</li>
 *   <li>{@link com.taskvoyage.config.TaskVoyageMysqlAutoConfiguration} - MySQL storage (default)</li>
 *   <li>{@link com.taskvoyage.config.TaskVoyageMongoAutoConfiguration} - MongoDB storage</li>
 * </ul>
 * Switch storage backend via {@code taskvoyage.storage.type} property.
 *
 * @see com.taskvoyage.config.TaskVoyageAutoConfiguration
 * @see com.taskvoyage.config.TaskVoyageMysqlAutoConfiguration
 * @see com.taskvoyage.config.TaskVoyageMongoAutoConfiguration
 * @see com.taskvoyage.engine.TaskVoyageEngine
 */
public final class TaskVoyageFramework {

    private TaskVoyageFramework() {
        // Utility class, not meant to be instantiated
    }

    /** Framework version — synchronized with pom.xml */
    public static final String VERSION = "1.0.0";

    /** Framework name */
    public static final String NAME = "taskvoyage-framework";
}
