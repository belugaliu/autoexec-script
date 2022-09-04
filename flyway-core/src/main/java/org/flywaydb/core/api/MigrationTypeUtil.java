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
package org.flywaydb.core.api;

 import lombok.AccessLevel;
import lombok.CustomLog;
import lombok.NoArgsConstructor;
import org.flywaydb.core.extensibility.MigrationType;

import java.text.MessageFormat;

/**
 *  enum implements MigrationType. now suppport:
 * {@link MigrationTypeUtil#name(MigrationType)} as {@link Enum#name()}
 * and {@link MigrationTypeUtil#fromString(String)} as {@link Enum#valueOf(Class, String)}.
 * if only normal/abstract class implements MigrationType, then will throw runtimeException to tell you that use incorrect.
 *
 * @author liull
 * @since 2022/4/14 18:21
 */
@CustomLog
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MigrationTypeUtil {

    public static String name(MigrationType migrationType) {
        if (migrationType.getClass().isEnum()) {
            for(Object enumObj: migrationType.getClass().getEnumConstants()) {
                if (enumObj == migrationType) {
                    return ((Enum)enumObj).name();
                }
            }
        }
        throw new IllegalStateException(MessageFormat.format(
                "MigrationType implement class must be enum, but class {0} not for the rule.",
                migrationType.getClass()));
    }

    public static MigrationType fromString(String migrationType) {
        if ("SPRING_JDBC".equals(migrationType)) {
            return CoreMigrationType.JDBC;
        }
        if ("UNDO_SPRING_JDBC".equals(migrationType)) {
            return CoreMigrationType.UNDO_JDBC;
        }
        ServiceLoader<MigrationType> loader = ServiceLoader.load(MigrationType.class);
        StringBuilder builder = new StringBuilder(256);
        for (MigrationType migrationTypeImpl : loader) {
            Class migrationTypeClass = migrationTypeImpl.getClass();
            builder.append(migrationTypeClass.getName()).append(",");
            try {
                if (migrationTypeClass.isEnum() && Enum.valueOf(migrationTypeClass, migrationType) != null) {
                    return (MigrationType)Enum.valueOf(migrationTypeClass, migrationType);
                }
            } catch (IllegalArgumentException ignore) {
                // ignore exception
            }
        }
        throw new IllegalStateException(MessageFormat.format(
                "MigrationType implement class must be enum, but migrationType {0} no match enum.",
                migrationType, builder.toString()));
    }
}
