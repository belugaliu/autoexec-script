import lombok.CustomLog;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.flywaydb.core.internal.scanner.LocationScannerCache;
import org.flywaydb.core.internal.scanner.ResourceNameCache;
import org.flywaydb.core.internal.scanner.Scanner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * flyway unit test. except for base flyway functions, for example:<br>
 *     1. multi-script in one version. <br>
 *     2. any script change auto execute again, and you can cancle feature by config <code>flyway.auto-cancle</code><br>
 * @author liull
 * @since 2022/7/30
 */
@CustomLog
public class FlywayTests {

    @Test
    public void flywayTest() {
        multiFiles();
        changeFiles();
        addFiles();
    }

    /**
     * test multi-files in one version.
     */
    public void multiFiles() {
        Flyway flyway = init();
        ClassicConfiguration configuration = (ClassicConfiguration) flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        cleanDB(dataSource, configuration);
        flyway.migrate();
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            String query = String.format("select count(*) from \"%s\".\"%s\" where \"type\"='SQL'",
                    configuration.getDefaultSchema(),
                    configuration.getTable());
            ResultSet rs = stmt.executeQuery(query);
            long count = 0;
            while (rs.next()) {
                count = rs.getLong(1);
            }
            long scriptCount = readScriptCount(configuration);
            Assertions.assertEquals(count, scriptCount);
        } catch (SQLException sqlException) {
            LOG.error("init connection exception.", sqlException);
        }
    }

    private void cleanDB(DataSource dataSource, Configuration configuration) {
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            String dropSchemaSql = String.format("drop schema if exists %s CASCADE",
                    configuration.getDefaultSchema());
            boolean success = stmt.execute(dropSchemaSql);
            LOG.info("drop schema success?" + success);
        } catch (SQLException sqlException) {
            LOG.error("init connection exception.", sqlException);
        }
    }

    private long readScriptCount(ClassicConfiguration configuration) {
        ResourceNameCache resourceNameCache = new ResourceNameCache();
        LocationScannerCache locationScannerCache = new LocationScannerCache();
        Scanner<JavaMigration> scanner = new Scanner<>(
                JavaMigration.class,
                Arrays.asList(configuration.getLocations()),
                configuration.getClassLoader(),
                configuration.getEncoding(),
                configuration.isDetectEncoding(),
                false,
                resourceNameCache,
                locationScannerCache,
                configuration.isFailOnMissingLocations());
        return scanner.getResources(configuration.getSqlMigrationPrefix(),
                "sql").size();
    }

    /**
     * change exec sql file, then have two record in flyway_schema_history table.
     * one is delete, and the other is SQL.
     */
    public void changeFiles() {
        String filerPath = "script/1/V1__02_init.sql";
        changeScriptToUpdate(filerPath);
        Flyway flyway = init();
        String currentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Calendar.getInstance().getTime());
        flyway.migrate();
        ClassicConfiguration configuration = (ClassicConfiguration) flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        try (Connection connection = dataSource.getConnection();
             // assert count
            Statement stmt = connection.createStatement()) {
            String query = String.format("select \"type\" from \"%s\".\"%s\" where \"script\"='V1__02_init.sql' and" +
                            "\"installed_on\">'%s' order by \"installed_on\" asc",
                    configuration.getDefaultSchema(),
                    configuration.getTable(), currentTime);
            ResultSet rs = stmt.executeQuery(query);
            List<String> types = new ArrayList<>(4);
            while (rs.next()) {
                types.add(rs.getString(1));
            }
            Assertions.assertEquals(String.join(";", types), "DELETE;SQL");
        } catch (SQLException sqlException) {
            LOG.error("init connection exception.", sqlException);
        }
    }

    /**
     * test at one version add delete script.
     */
    public void addFiles() {
        String filePath = "V1__03_delete.sql";
        String filerPath = "script/1/V1__02_init.sql";
        createDeleteScript(filerPath, filePath);
        Flyway flyway = init();
        flyway.migrate();
        ClassicConfiguration configuration = (ClassicConfiguration) flyway.getConfiguration();
        DataSource dataSource = configuration.getDataSource();
        try (Connection connection = dataSource.getConnection();
             Statement stmt = connection.createStatement()) {
            String query = String.format("select count(*) from \"%s\".\"%s\" where \"script\"='V1__03_delete.sql'",
                    configuration.getDefaultSchema(),
                    configuration.getTable());
            ResultSet rs = stmt.executeQuery(query);
            int count = -1;
            while (rs.next()) {
                count = rs.getInt(1);
            }
            Assertions.assertEquals(count, 1);
        } catch (SQLException sqlException) {
            LOG.error("init connection exception.", sqlException);
        }
    }

    private void createDeleteScript(String filerPath, String filePath) {
        String initScriptPath = this.getClass().getClassLoader().getResource(filerPath).getPath();
        String parentPath = new File(initScriptPath).getParent();
        File deleteFile = new File(parentPath, filePath);
        deleteFile.deleteOnExit();
        try {
            deleteFile.createNewFile();
        } catch (IOException e) {
            LOG.error("delete file create error", e);
        }
        try(FileWriter writer = new FileWriter(deleteFile)) {
            writer.write("delete AUTOEXEC.EMP_INFO where EMP_NAME='john';");
        } catch (IOException e) {
            LOG.error("file may not exists", e);
        }
    }

    private void changeScriptToUpdate(String filerPath) {
        String initScriptPath = this.getClass().getClassLoader().getResource(filerPath).getPath();
        try(FileWriter writer = new FileWriter(initScriptPath)) {
            writer.write(String.format("update AUTOEXEC.EMP_INFO set EMP_AGE = %s where EMP_NAME='john';", new Random().nextInt(1000)));
        } catch (IOException e) {
            LOG.error("file may not exists", e);
        }
    }


    private Flyway init() {
        FluentConfiguration configuration = new FluentConfiguration();
        String filePath = this.getClass().getClassLoader().getResource("conf/autoexec.conf").getFile();
        Map<String, String> configMap = ConfigUtils.loadDefaultConfigurationFiles(
                new File(filePath).getParentFile().getParentFile(),
                StandardCharsets.UTF_8.name());
        configuration.configuration(configMap);
        return configuration.load();
    }
}
