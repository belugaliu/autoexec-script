package json;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.extensibility.MigrationType;

@Getter
@RequiredArgsConstructor
public enum JsonMigrationType implements MigrationType {
    JSON(true, false, false);

    /**
     * @return Whether this is a synthetic migration type, which is only ever present in the schema history table,
     * but never discovered by migration resolvers.
     */
    private final boolean synthetic;
    /**
     * @return Whether this is an undo migration, which has undone an earlier migration present in the schema history table.
     */
    private final boolean undo;
    /**
     * @return Whether this is a baseline type
     */
    private final boolean baseline;

}
