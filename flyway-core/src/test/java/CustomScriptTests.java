import json.resolver.JsonMigrationResolver;
import lombok.CustomLog;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.internal.configuration.ConfigUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * custom script type for execute unit test. except for base flyway functions.
 * @author liull
 * @since 2022/09/03
 */
@CustomLog
public class CustomScriptTests {

    @Test
    public void test() {
        customJsonType();
    }

    private void customJsonType() {
        FluentConfiguration configuration = new FluentConfiguration();
        String filePath = this.getClass().getClassLoader().getResource("conf/autoexec.conf").getFile();
        Map<String, String> configMap = ConfigUtils.loadDefaultConfigurationFiles(
                new File(filePath).getParentFile().getParentFile(),
                StandardCharsets.UTF_8.name());
        configuration.configuration(configMap);
        JsonMigrationResolver jsonMigrationResolver = new JsonMigrationResolver(new String[]{".json"}, "V");
        configuration.resolvers(jsonMigrationResolver);
        Flyway flyway = configuration.load();
        flyway.migrate();
    }
}
