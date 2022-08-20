/*
 * Copyright (C) Red Gate Software Ltd 2010-2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flywaydb.core.internal.configuration;

import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.extensibility.ConfigurationExtension;
import org.flywaydb.core.internal.database.DatabaseTypeRegister;
import org.flywaydb.core.internal.plugin.PluginRegister;
import org.flywaydb.core.internal.util.FileUtils;
import org.flywaydb.core.internal.util.StringUtils;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.flywaydb.core.internal.sqlscript.SqlScriptMetadata.isMultilineBooleanExpression;

@CustomLog
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ConfigUtils {
    public static final String CONFIG_FILE_NAME = "autoexec.conf";
    public static final String CONFIG_FILES = "autoexec.configFiles";
    public static final String CONFIG_FILE_ENCODING = "autoexec.configFileEncoding";
    public static final String BASELINE_DESCRIPTION = "autoexec.baselineDescription";
    public static final String BASELINE_ON_MIGRATE = "autoexec.baselineOnMigrate";
    public static final String BASELINE_VERSION = "autoexec.baselineVersion";
    public static final String BATCH = "autoexec.batch";
    public static final String CALLBACKS = "autoexec.callbacks";
    public static final String CLEAN_DISABLED = "autoexec.cleanDisabled";
    public static final String CLEAN_ON_VALIDATION_ERROR = "autoexec.cleanOnValidationError";
    public static final String CONNECT_RETRIES = "autoexec.connectRetries";
    public static final String CONNECT_RETRIES_INTERVAL = "autoexec.connectRetriesInterval";
    public static final String DEFAULT_SCHEMA = "autoexec.defaultSchema";
    public static final String DRIVER = "autoexec.driver";
    public static final String DRYRUN_OUTPUT = "autoexec.dryRunOutput";
    public static final String ENCODING = "autoexec.encoding";
    public static final String DETECT_ENCODING = "autoexec.detectEncoding";
    public static final String ERROR_OVERRIDES = "autoexec.errorOverrides";
    public static final String GROUP = "autoexec.group";
    public static final String IGNORE_MIGRATION_PATTERNS = "autoexec.ignoreMigrationPatterns";
    public static final String INIT_SQL = "autoexec.initSql";
    public static final String INSTALLED_BY = "autoexec.installedBy";
    public static final String LICENSE_KEY = "autoexec.licenseKey";
    public static final String LOCATIONS = "autoexec.locations";
    public static final String MIXED = "autoexec.mixed";
    public static final String OUT_OF_ORDER = "autoexec.outOfOrder";
    public static final String SKIP_EXECUTING_MIGRATIONS = "autoexec.skipExecutingMigrations";
    public static final String OUTPUT_QUERY_RESULTS = "autoexec.outputQueryResults";
    public static final String PASSWORD = "autoexec.password";
    public static final String PLACEHOLDER_PREFIX = "autoexec.placeholderPrefix";
    public static final String PLACEHOLDER_REPLACEMENT = "autoexec.placeholderReplacement";
    public static final String PLACEHOLDER_SUFFIX = "autoexec.placeholderSuffix";
    public static final String PLACEHOLDER_SEPARATOR = "autoexec.placeholderSeparator";
    public static final String SCRIPT_PLACEHOLDER_PREFIX = "autoexec.scriptPlaceholderPrefix";
    public static final String SCRIPT_PLACEHOLDER_SUFFIX = "autoexec.scriptPlaceholderSuffix";
    public static final String PLACEHOLDERS_PROPERTY_PREFIX = "autoexec.placeholders.";
    public static final String LOCK_RETRY_COUNT = "autoexec.lockRetryCount";
    public static final String JDBC_PROPERTIES_PREFIX = "autoexec.jdbcProperties.";
    public static final String REPEATABLE_SQL_MIGRATION_PREFIX = "autoexec.repeatableSqlMigrationPrefix";
    public static final String RESOLVERS = "autoexec.resolvers";
    public static final String SCHEMAS = "autoexec.schemas";
    public static final String SKIP_DEFAULT_CALLBACKS = "autoexec.skipDefaultCallbacks";
    public static final String SKIP_DEFAULT_RESOLVERS = "autoexec.skipDefaultResolvers";
    public static final String SQL_MIGRATION_PREFIX = "autoexec.sqlMigrationPrefix";
    public static final String SQL_MIGRATION_SEPARATOR = "autoexec.sqlMigrationSeparator";
    public static final String SQL_MIGRATION_SUFFIXES = "autoexec.sqlMigrationSuffixes";
    public static final String STREAM = "autoexec.stream";
    public static final String TABLE = "autoexec.table";
    public static final String TABLESPACE = "autoexec.tablespace";
    public static final String TARGET = "autoexec.target";
    public static final String CHERRY_PICK = "autoexec.cherryPick";
    public static final String UNDO_SQL_MIGRATION_PREFIX = "autoexec.undoSqlMigrationPrefix";
    public static final String URL = "autoexec.url";
    public static final String USER = "autoexec.user";
    public static final String VALIDATE_ON_MIGRATE = "autoexec.validateOnMigrate";
    public static final String VALIDATE_MIGRATION_NAMING = "autoexec.validateMigrationNaming";
    public static final String CREATE_SCHEMAS = "autoexec.createSchemas";
    public static final String FAIL_ON_MISSING_LOCATIONS = "autoexec.failOnMissingLocations";
    public static final String LOGGERS = "autoexec.loggers";
    public static final String KERBEROS_CONFIG_FILE = "autoexec.kerberosConfigFile";

    // Oracle-specific
    public static final String ORACLE_SQLPLUS = "autoexec.oracle.sqlplus";
    public static final String ORACLE_SQLPLUS_WARN = "autoexec.oracle.sqlplusWarn";
    public static final String ORACLE_KERBEROS_CACHE_FILE = "autoexec.oracle.kerberosCacheFile";
    public static final String ORACLE_WALLET_LOCATION = "autoexec.oracle.walletLocation";

    // Command-line specific
    public static final String JAR_DIRS = "autoexec.jarDirs";

    // Gradle specific
    public static final String CONFIGURATIONS = "autoexec.configurations";

    // Plugin specific
    public static final String AUTOEXEC_PLUGINS_PREFIX = "autoexec.plugins.";

    /**
     * Converts Flyway-specific environment variables to their matching properties.
     *
     * @return The properties corresponding to the environment variables.
     */
    public static Map<String, String> environmentVariablesToPropertyMap() {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            String convertedKey = convertKey(entry.getKey());
            if (convertedKey != null) {
                // Known environment variable
                result.put(convertKey(entry.getKey()), entry.getValue());
            }
        }

        return result;
    }

    private static String convertKey(String key) {
        if ("AUTOEXEC_BASELINE_DESCRIPTION".equals(key)) {
            return BASELINE_DESCRIPTION;
        }
        if ("AUTOEXEC_BASELINE_ON_MIGRATE".equals(key)) {
            return BASELINE_ON_MIGRATE;
        }
        if ("AUTOEXEC_BASELINE_VERSION".equals(key)) {
            return BASELINE_VERSION;
        }
        if ("AUTOEXEC_BATCH".equals(key)) {
            return BATCH;
        }
        if ("AUTOEXEC_CALLBACKS".equals(key)) {
            return CALLBACKS;
        }
        if ("AUTOEXEC_CLEAN_DISABLED".equals(key)) {
            return CLEAN_DISABLED;
        }
        if ("AUTOEXEC_CLEAN_ON_VALIDATION_ERROR".equals(key)) {
            return CLEAN_ON_VALIDATION_ERROR;
        }
        if ("AUTOEXEC_CONFIG_FILE_ENCODING".equals(key)) {
            return CONFIG_FILE_ENCODING;
        }
        if ("AUTOEXEC_CONFIG_FILES".equals(key)) {
            return CONFIG_FILES;
        }
        if ("AUTOEXEC_CONNECT_RETRIES".equals(key)) {
            return CONNECT_RETRIES;
        }

        if ("AUTOEXEC_CONNECT_RETRIES_INTERVAL".equals(key)) {
            return CONNECT_RETRIES_INTERVAL;
        }

        if ("AUTOEXEC_DEFAULT_SCHEMA".equals(key)) {
            return DEFAULT_SCHEMA;
        }
        if ("AUTOEXEC_DRIVER".equals(key)) {
            return DRIVER;
        }
        if ("AUTOEXEC_DRYRUN_OUTPUT".equals(key)) {
            return DRYRUN_OUTPUT;
        }
        if ("AUTOEXEC_ENCODING".equals(key)) {
            return ENCODING;
        }
        if ("AUTOEXEC_DETECT_ENCODING".equals(key)) {
            return DETECT_ENCODING;
        }
        if ("AUTOEXEC_ERROR_OVERRIDES".equals(key)) {
            return ERROR_OVERRIDES;
        }
        if ("AUTOEXEC_GROUP".equals(key)) {
            return GROUP;
        }
        if ("AUTOEXEC_IGNORE_MIGRATION_PATTERNS".equals(key)) {
            return IGNORE_MIGRATION_PATTERNS;
        }
        if ("AUTOEXEC_INIT_SQL".equals(key)) {
            return INIT_SQL;
        }
        if ("AUTOEXEC_INSTALLED_BY".equals(key)) {
            return INSTALLED_BY;
        }
        if ("AUTOEXEC_LICENSE_KEY".equals(key)) {
            return LICENSE_KEY;
        }
        if ("AUTOEXEC_LOCATIONS".equals(key)) {
            return LOCATIONS;
        }
        if ("AUTOEXEC_MIXED".equals(key)) {
            return MIXED;
        }
        if ("AUTOEXEC_OUT_OF_ORDER".equals(key)) {
            return OUT_OF_ORDER;
        }
        if ("AUTOEXEC_SKIP_EXECUTING_MIGRATIONS".equals(key)) {
            return SKIP_EXECUTING_MIGRATIONS;
        }
        if ("AUTOEXEC_OUTPUT_QUERY_RESULTS".equals(key)) {
            return OUTPUT_QUERY_RESULTS;
        }
        if ("AUTOEXEC_PASSWORD".equals(key)) {
            return PASSWORD;
        }
        if ("AUTOEXEC_LOCK_RETRY_COUNT".equals(key)) {
            return LOCK_RETRY_COUNT;
        }
        if ("AUTOEXEC_PLACEHOLDER_PREFIX".equals(key)) {
            return PLACEHOLDER_PREFIX;
        }
        if ("AUTOEXEC_PLACEHOLDER_REPLACEMENT".equals(key)) {
            return PLACEHOLDER_REPLACEMENT;
        }
        if ("AUTOEXEC_PLACEHOLDER_SUFFIX".equals(key)) {
            return PLACEHOLDER_SUFFIX;
        }
        if ("AUTOEXEC_PLACEHOLDER_SEPARATOR".equals(key)) {
            return PLACEHOLDER_SEPARATOR;
        }
        if ("AUTOEXEC_SCRIPT_PLACEHOLDER_PREFIX".equals(key)) {
            return SCRIPT_PLACEHOLDER_PREFIX;
        }
        if ("AUTOEXEC_SCRIPT_PLACEHOLDER_SUFFIX".equals(key)) {
            return SCRIPT_PLACEHOLDER_SUFFIX;
        }
        if (key.matches("AUTOEXEC_PLACEHOLDERS_.+")) {
            return PLACEHOLDERS_PROPERTY_PREFIX + key.substring("AUTOEXEC_PLACEHOLDERS_".length()).toLowerCase(Locale.ENGLISH);
        }

        if (key.matches("AUTOEXEC_JDBC_PROPERTIES_.+")) {
            return JDBC_PROPERTIES_PREFIX + key.substring("AUTOEXEC_JDBC_PROPERTIES_".length());
        }

        if ("AUTOEXEC_REPEATABLE_SQL_MIGRATION_PREFIX".equals(key)) {
            return REPEATABLE_SQL_MIGRATION_PREFIX;
        }
        if ("AUTOEXEC_RESOLVERS".equals(key)) {
            return RESOLVERS;
        }
        if ("AUTOEXEC_SCHEMAS".equals(key)) {
            return SCHEMAS;
        }
        if ("AUTOEXEC_SKIP_DEFAULT_CALLBACKS".equals(key)) {
            return SKIP_DEFAULT_CALLBACKS;
        }
        if ("AUTOEXEC_SKIP_DEFAULT_RESOLVERS".equals(key)) {
            return SKIP_DEFAULT_RESOLVERS;
        }
        if ("AUTOEXEC_SQL_MIGRATION_PREFIX".equals(key)) {
            return SQL_MIGRATION_PREFIX;
        }
        if ("AUTOEXEC_SQL_MIGRATION_SEPARATOR".equals(key)) {
            return SQL_MIGRATION_SEPARATOR;
        }
        if ("AUTOEXEC_SQL_MIGRATION_SUFFIXES".equals(key)) {
            return SQL_MIGRATION_SUFFIXES;
        }
        if ("AUTOEXEC_STREAM".equals(key)) {
            return STREAM;
        }
        if ("AUTOEXEC_TABLE".equals(key)) {
            return TABLE;
        }
        if ("AUTOEXEC_TABLESPACE".equals(key)) {
            return TABLESPACE;
        }
        if ("AUTOEXEC_TARGET".equals(key)) {
            return TARGET;
        }
        if ("AUTOEXEC_CHERRY_PICK".equals(key)) {
            return CHERRY_PICK;
        }
        if ("AUTOEXEC_LOGGERS".equals(key)) {
            return LOGGERS;
        }
        if ("AUTOEXEC_UNDO_SQL_MIGRATION_PREFIX".equals(key)) {
            return UNDO_SQL_MIGRATION_PREFIX;
        }
        if ("AUTOEXEC_URL".equals(key)) {
            return URL;
        }
        if ("AUTOEXEC_USER".equals(key)) {
            return USER;
        }
        if ("AUTOEXEC_VALIDATE_ON_MIGRATE".equals(key)) {
            return VALIDATE_ON_MIGRATE;
        }
        if ("AUTOEXEC_VALIDATE_MIGRATION_NAMING".equals(key)) {
            return VALIDATE_MIGRATION_NAMING;
        }
        if ("AUTOEXEC_CREATE_SCHEMAS".equals(key)) {
            return CREATE_SCHEMAS;
        }
        if ("AUTOEXEC_FAIL_ON_MISSING_LOCATIONS".equals(key)) {
            return FAIL_ON_MISSING_LOCATIONS;
        }
        if ("AUTOEXEC_KERBEROS_CONFIG_FILE".equals(key)) {
            return KERBEROS_CONFIG_FILE;
        }

        // Oracle-specific
        if ("AUTOEXEC_ORACLE_SQLPLUS".equals(key)) {
            return ORACLE_SQLPLUS;
        }
        if ("AUTOEXEC_ORACLE_SQLPLUS_WARN".equals(key)) {
            return ORACLE_SQLPLUS_WARN;
        }
        if ("AUTOEXEC_ORACLE_KERBEROS_CACHE_FILE".equals(key)) {
            return ORACLE_KERBEROS_CACHE_FILE;
        }
        if ("AUTOEXEC_ORACLE_WALLET_LOCATION".equals(key)) {
            return ORACLE_WALLET_LOCATION;
        }

        // Command-line specific
        if ("AUTOEXEC_JAR_DIRS".equals(key)) {
            return JAR_DIRS;
        }

        // Gradle specific
        if ("AUTOEXEC_CONFIGURATIONS".equals(key)) {
            return CONFIGURATIONS;
        }

        for (ConfigurationExtension configurationExtension : new PluginRegister().getPlugins(ConfigurationExtension.class)) {
            String configurationParameter = configurationExtension.getConfigurationParameterFromEnvironmentVariable(key);
            if (configurationParameter != null) {
                return configurationParameter;
            }
        }

        return null;
    }

    /**
     * Load configuration files from the default locations:
     * $installationDir$/conf/autoexec.conf
     * $user.home$/autoexec.conf
     * $workingDirectory$/autoexec.conf
     *
     * @param encoding The conf file encoding.
     * @throws FlywayException When the configuration failed.
     */
    public static Map<String, String> loadDefaultConfigurationFiles(File installationDir, String encoding) {
        Map<String, String> configMap = new HashMap<>();
        configMap.putAll(ConfigUtils.loadConfigurationFile(new File(installationDir.getAbsolutePath() + "/conf/" + ConfigUtils.CONFIG_FILE_NAME), encoding, false));
        configMap.putAll(ConfigUtils.loadConfigurationFile(new File(System.getProperty("user.home") + "/" + ConfigUtils.CONFIG_FILE_NAME), encoding, false));
        configMap.putAll(ConfigUtils.loadConfigurationFile(new File(ConfigUtils.CONFIG_FILE_NAME), encoding, false));

        return configMap;
    }

    /**
     * Loads the configuration from this configuration file.
     *
     * @param configFile The configuration file to load.
     * @param encoding The encoding of the configuration file.
     * @param failIfMissing Whether to fail if the file is missing.
     * @return The properties from the configuration file. An empty Map if none.
     * @throws FlywayException When the configuration file could not be loaded.
     */
    public static Map<String, String> loadConfigurationFile(File configFile, String encoding, boolean failIfMissing) throws FlywayException {
        String errorMessage = "Unable to load config file: " + configFile.getAbsolutePath();

        if ("-".equals(configFile.getName())) {
            return loadConfigurationFromInputStream(System.in);
        } else if (!configFile.isFile() || !configFile.canRead()) {
            if (!failIfMissing) {
                LOG.debug(errorMessage);
                return new HashMap<>();
            }
            throw new FlywayException(errorMessage);
        }

        LOG.debug("Loading config file: " + configFile.getAbsolutePath());

        try {
            return loadConfigurationFromReader(new InputStreamReader(new FileInputStream(configFile), encoding));
        } catch (IOException | FlywayException e) {
            throw new FlywayException(errorMessage, e);
        }
    }

    public static Map<String, String> loadConfigurationFromInputStream(InputStream inputStream) {
        Map<String, String> config = new HashMap<>();

        try {
            // System.in.available() : returns an estimate of the number of bytes that can be read (or skipped over) from this input stream
            // Used to check if there is any data in the stream
            if (inputStream != null && inputStream.available() > 0) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                LOG.debug("Attempting to load configuration from standard input");
                int firstCharacter = bufferedReader.read();

                if (bufferedReader.ready() && firstCharacter != -1) {
                    // Prepend the first character to the rest of the string
                    // This is a char, represented as an int, so we cast to a char
                    // which is implicitly converted to an string
                    String configurationString = (char) firstCharacter + FileUtils.copyToString(bufferedReader);
                    Map<String, String> configurationFromStandardInput = loadConfigurationFromString(configurationString);

                    if (configurationFromStandardInput.isEmpty()) {
                        LOG.debug("Empty configuration provided from standard input");
                    } else {
                        LOG.info("Loaded configuration from standard input");
                        config.putAll(configurationFromStandardInput);
                    }
                } else {
                    LOG.debug("Could not load configuration from standard input");
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not load configuration from standard input " + e.getMessage());
        }

        return config;
    }

    /**
     * Reads the configuration from a Reader.
     *
     * @return The properties from the configuration file. An empty Map if none.
     * @throws FlywayException When the configuration could not be read.
     */
    public static Map<String, String> loadConfigurationFromReader(Reader reader) throws FlywayException {
        try {
            String contents = FileUtils.copyToString(reader);
            return loadConfigurationFromString(contents);
        } catch (IOException e) {
            throw new FlywayException("Unable to read config", e);
        }
    }

    public static Map<String, String> loadConfigurationFromString(String configuration) throws IOException {
        String[] lines = configuration.replace("\r\n", "\n").split("\n");

        StringBuilder confBuilder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String replacedLine = lines[i].trim().replace("\\", "\\\\");

            // if the line ends in a \\, then it may be a multiline property
            if (replacedLine.endsWith("\\\\")) {
                // if we aren't the last line
                if (i < lines.length - 1) {
                    // look ahead to see if the next line is a blank line, a property, or another multiline
                    String nextLine = lines[i + 1];
                    boolean restoreMultilineDelimiter = false;
                    if (nextLine.isEmpty()) {
                        // blank line
                    } else if (nextLine.trim().startsWith("autoexec.") && nextLine.contains("=")) {
                        if (isMultilineBooleanExpression(nextLine)) {
                            // next line is an extension of a boolean expression
                            restoreMultilineDelimiter = true;
                        }
                        // next line is a property
                    } else {
                        // line with content, this was a multiline property
                        restoreMultilineDelimiter = true;
                    }

                    if (restoreMultilineDelimiter) {
                        // it's a multiline property, so restore the original single slash
                        replacedLine = replacedLine.substring(0, replacedLine.length() - 2) + "\\";
                    }
                }
            }

            confBuilder.append(replacedLine).append("\n");
        }
        String contents = confBuilder.toString();

        Properties properties = new Properties();
        contents = expandEnvironmentVariables(contents, System.getenv());
        properties.load(new StringReader(contents));
        return propertiesToMap(properties);
    }

    static String expandEnvironmentVariables(String value, Map<String, String> environmentVariables) {
        Pattern pattern = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
        Matcher matcher = pattern.matcher(value);
        String expandedValue = value;

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String variableValue = environmentVariables.getOrDefault(variableName, "");

            LOG.debug("Expanding environment variable in config: " + variableName + " -> " + variableValue);
            expandedValue = expandedValue.replaceAll(Pattern.quote(matcher.group(0)), Matcher.quoteReplacement(variableValue));
        }

        return expandedValue;
    }

    /**
     * Converts this Properties object into a map.
     */
    public static Map<String, String> propertiesToMap(Properties properties) {
        Map<String, String> props = new HashMap<>();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            props.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return props;
    }

    /**
     * Puts this property in the config if it has been set in any of these values.
     *
     * @param config The config.
     * @param key The property name.
     * @param values The values to try. The first non-null value will be set.
     */
    public static void putIfSet(Map<String, String> config, String key, Object... values) {
        for (Object value : values) {
            if (value != null) {
                config.put(key, value.toString());
                return;
            }
        }
    }

    /**
     * Puts this property in the config if it has been set in any of these values.
     *
     * @param config The config.
     * @param key The property name.
     * @param values The values to try. The first non-null value will be set.
     */
    public static void putArrayIfSet(Map<String, String> config, String key, String[]... values) {
        for (String[] value : values) {
            if (value != null) {
                config.put(key, StringUtils.arrayToCommaDelimitedString(value));
                return;
            }
        }
    }

    /**
     * @param config The config.
     * @param key The property name.
     * @return The property value as a boolean if it exists, otherwise {@code null}.
     * @throws FlywayException when the property value is not a valid boolean.
     */
    public static Boolean removeBoolean(Map<String, String> config, String key) {
        String value = config.remove(key);
        if (value == null) {
            return null;
        }
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new FlywayException("Invalid value for " + key + " (should be either true or false): " + value,
                                      ErrorCode.CONFIGURATION);
        }
        return Boolean.valueOf(value);
    }

    /**
     * @param config The config.
     * @param key The property name.
     * @return The property value as an integer if it exists, otherwise {@code null}.
     * @throws FlywayException When the property value is not a valid integer.
     */
    public static Integer removeInteger(Map<String, String> config, String key) {
        String value = config.remove(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            throw new FlywayException("Invalid value for " + key + " (should be an integer): " + value,
                                      ErrorCode.CONFIGURATION);
        }
    }

    public static void dumpConfiguration(Map<String, String> config) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Using configuration:");
            for (Map.Entry<String, String> entry : new TreeMap<>(config).entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key.toLowerCase().endsWith("password")) {
                    value = StringUtils.trimOrPad("", value.length(), '*');
                } else if (ConfigUtils.LICENSE_KEY.equals(key)) {
                    value = value.substring(0, 8) + "******" + value.substring(value.length() - 4);
                } else if (ConfigUtils.URL.equals(key)) {
                    value = DatabaseTypeRegister.redactJdbcUrl(value);
                }

                LOG.debug(key + " -> " + value);
            }
        }
    }

    /**
     * Checks the configuration for any unrecognised properties remaining after expected ones have been consumed.
     *
     * @param config The configured properties.
     * @param prefix The expected prefix for Flyway configuration parameters. {@code null} if none.
     */
    public static void checkConfigurationForUnrecognisedProperties(Map<String, String> config, String prefix) {
        ArrayList<String> unknownFlywayProperties = new ArrayList<>();
        for (String key : config.keySet()) {
            if (prefix == null || (key.startsWith(prefix) && !key.startsWith(AUTOEXEC_PLUGINS_PREFIX))) {
                unknownFlywayProperties.add(key);
            }
        }

        if (!unknownFlywayProperties.isEmpty()) {
            String property = (unknownFlywayProperties.size() == 1) ? "property" : "properties";
            String message = String.format("Unknown configuration %s: %s",
                                           property,
                                           StringUtils.arrayToCommaDelimitedString(unknownFlywayProperties.toArray()));
            throw new FlywayException(message, ErrorCode.CONFIGURATION);
        }
    }
}