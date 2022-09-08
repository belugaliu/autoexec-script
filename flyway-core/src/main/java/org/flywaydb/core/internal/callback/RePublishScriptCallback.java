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
package org.flywaydb.core.internal.callback;

import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.CustomLog;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.BaseCallback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.extensibility.AppliedMigration;
import org.flywaydb.core.internal.database.base.Database;
import org.flywaydb.core.internal.info.MigrationInfoImpl;
import org.flywaydb.core.internal.info.MigrationInfoServiceImpl;
import org.flywaydb.core.internal.resolver.CompositeMigrationResolver;
import org.flywaydb.core.internal.schemahistory.SchemaHistory;
import org.flywaydb.core.internal.util.ValidatePatternUtils;
import software.amazon.awssdk.utils.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * corrected script to allow execution.The principle is to mark the historical execution record as deleted,
 * the subsequent code will regard the historical execution record as invalid,
 * and re-execute the modified script.
 * @author liull
 */
@CustomLog
@Builder
@AllArgsConstructor
public class RePublishScriptCallback extends BaseCallback {

    private Database database;

    private CompositeMigrationResolver migrationResolver;

    private SchemaHistory schemaHistory;

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_VALIDATE;
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        Configuration configuration = context.getConfiguration();
        MigrationInfoServiceImpl migrationInfoService = new MigrationInfoServiceImpl(migrationResolver, schemaHistory, database, configuration,
                configuration.getTarget(), configuration.isOutOfOrder(), ValidatePatternUtils.getIgnoreAllPattern(), configuration.getCherryPick());
        migrationInfoService.refresh();
        MigrationInfo[] migrationInfos = migrationInfoService.all();
        List<MigrationInfoImpl> migrationInfoList = Arrays.stream(migrationInfos)
                .map(migrationInfo -> {
                    if (migrationInfo instanceof MigrationInfoImpl) {
                        return (MigrationInfoImpl) migrationInfo;
                    }
                    return null;
                })
                .filter(migrationInfoImpl -> {
                    if (migrationInfoImpl == null) {
                        return false;
                    }
                    AppliedMigration appliedMigration = migrationInfoImpl.getAppliedMigration();
                    ResolvedMigration resolvedMigration = migrationInfoImpl.getResolvedMigration();
                    if (appliedMigration == null || resolvedMigration == null) {
                        return false;
                    }
                    return StringUtils.equals(appliedMigration.getScript(), resolvedMigration.getScript());
                }).sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        List<MigrationInfoImpl> checksumMigrationInfos = new ArrayList<>();
        Set<String> scripts = new HashSet<>();
        migrationInfoList.forEach(migrationInfoImpl -> {
            if (scripts.contains(migrationInfoImpl.getScript())) {
                return;
            }
            checksumMigrationInfos.add(migrationInfoImpl);
            scripts.add(migrationInfoImpl.getAppliedMigration().getScript());
        });

        checksumMigrationInfos.stream()
                .filter(migrationInfoImpl ->
                        !Objects.equals(migrationInfoImpl.getAppliedMigration().getChecksum(),
                                migrationInfoImpl.getResolvedMigration().getChecksum())
                                && !migrationInfoImpl.getAppliedMigration().getScript().startsWith(configuration.getOSqlMigrationPrefix())
                                && migrationInfoImpl.getAppliedMigration().getType() != CoreMigrationType.DELETE)
                .forEach(migrationInfoImpl -> {
                    schemaHistory.delete(migrationInfoImpl.getAppliedMigration());
                    LOG.info("When modifying the executed script, mark and delete the script execution record"
                                    + new GsonBuilder().create().toJson(migrationInfoImpl.getAppliedMigration())
                                    + ", and trigger the execution of the modified script with the outOfOrder configuration");
                });
    }

    @Override
    public void setDatabase(Database database) {
        this.database = database;
    }

    @Override
    public void setMigrationResolver(CompositeMigrationResolver migrationResolver) {
        this.migrationResolver = migrationResolver;
    }

    @Override
    public void setSchemaHistory(SchemaHistory schemaHistory) {
        this.schemaHistory = schemaHistory;
    }
}