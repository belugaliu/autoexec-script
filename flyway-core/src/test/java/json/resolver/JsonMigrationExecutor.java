package json.resolver;

import lombok.CustomLog;
import org.flywaydb.core.api.executor.Context;
import org.flywaydb.core.api.executor.MigrationExecutor;
import org.flywaydb.core.api.resource.LoadableResource;
import org.h2.util.IOUtils;

import java.io.IOException;
import java.sql.SQLException;

@CustomLog
public class JsonMigrationExecutor implements MigrationExecutor {

    private LoadableResource resource;
    public JsonMigrationExecutor(LoadableResource resource) {
        this.resource = resource;
    }

    @Override
    public void execute(Context context) throws SQLException {
        try {
            LOG.info(IOUtils.readStringAndClose(this.resource.read(), -1));
        } catch (IOException e) {
            LOG.error("", e);
        }
    }

    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    @Override
    public boolean shouldExecute() {
        return true;
    }
}
