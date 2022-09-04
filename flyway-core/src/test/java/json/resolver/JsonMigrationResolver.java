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
package json.resolver;

import json.JsonMigrationType;
import lombok.CustomLog;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.resolver.MigrationResolver;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.parser.ParserContext;
import org.flywaydb.core.internal.parser.ParsingContext;
import org.flywaydb.core.internal.parser.PlaceholderReplacingReader;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.flywaydb.core.internal.resolver.ResolvedMigrationComparator;
import org.flywaydb.core.internal.resolver.ResolvedMigrationImpl;
import org.flywaydb.core.internal.resolver.sql.SqlMigrationExecutor;
import org.flywaydb.core.internal.resource.ResourceName;
import org.flywaydb.core.internal.resource.ResourceNameParser;
import org.flywaydb.core.internal.sqlscript.SqlScript;
import org.flywaydb.core.internal.sqlscript.SqlScriptExecutorFactory;
import org.flywaydb.core.internal.sqlscript.SqlScriptFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Migration resolver for JSON files on the classpath. The SQL files must have names like
 * V1__Description.json, V1_1__Description.json, or R__description.json.
 */
@CustomLog
public class JsonMigrationResolver implements MigrationResolver {

    private String[] suffixes;

    private String prefix;

    public JsonMigrationResolver(String[] suffixes, String prefix) {
        this.suffixes = suffixes;
        this.prefix = prefix;
    }
    @Override
    public List<ResolvedMigration> resolveMigrations(Context context) {
        List<ResolvedMigration> migrations = new ArrayList<>();
        addMigrations(migrations, prefix, suffixes,
                      false, context);


        migrations.sort(new ResolvedMigrationComparator());
        return migrations;
    }

    private LoadableResource[] createPlaceholderReplacingLoadableResources(List<LoadableResource> loadableResources,
                                                                           Configuration configuration, ParsingContext parsingContext) {
        List<LoadableResource> list = new ArrayList<>();

        for (final LoadableResource loadableResource : loadableResources) {
            LoadableResource placeholderReplacingLoadableResource = new LoadableResource() {
                @Override
                public Reader read() {
                    return PlaceholderReplacingReader.create(configuration, parsingContext, loadableResource.read());
                }

                @Override
                public String getAbsolutePath() {return loadableResource.getAbsolutePath();}

                @Override
                public String getAbsolutePathOnDisk() {return loadableResource.getAbsolutePathOnDisk();}

                @Override
                public String getFilename() {return loadableResource.getFilename();}

                @Override
                public String getRelativePath() {return loadableResource.getRelativePath();}
            };

            list.add(placeholderReplacingLoadableResource);
        }

        return list.toArray(new LoadableResource[0]);
    }

    private Integer getChecksumForLoadableResource(boolean repeatable, List<LoadableResource> loadableResources, ResourceName resourceName,
                                                   Configuration configuration,ParsingContext parsingContext) {
        if (repeatable && configuration.isPlaceholderReplacement()) {
            parsingContext.updateFilenamePlaceholder(resourceName, configuration);
            return ChecksumCalculator.calculate(createPlaceholderReplacingLoadableResources(loadableResources, configuration, parsingContext));
        }

        return ChecksumCalculator.calculate(loadableResources.toArray(new LoadableResource[0]));
    }

    private Integer getEquivalentChecksumForLoadableResource(boolean repeatable, List<LoadableResource> loadableResources) {
        if (repeatable) {
            return ChecksumCalculator.calculate(loadableResources.toArray(new LoadableResource[0]));
        }

        return null;
    }

    private void addMigrations(List<ResolvedMigration> migrations, String prefix, String[] suffixes,
                               boolean repeatable, Context context) {
        Configuration configuration = context.configuration;
        ResourceProvider resourceProvider = context.resourceProvider;

        ResourceNameParser resourceNameParser = new ResourceNameParser(configuration);

        for (LoadableResource resource : resourceProvider.getResources(prefix, suffixes)) {
            String filename = resource.getFilename();
            ResourceName resourceName = resourceNameParser.parse(filename, this.suffixes);
            if (!resourceName.isValid() || !prefix.equals(resourceName.getPrefix())) {
                continue;
            }

            List<LoadableResource> resources = new ArrayList<>();
            resources.add(resource);
            Integer checksum = getChecksumForLoadableResource(repeatable, resources, resourceName, configuration, context.parsingContext);
            Integer equivalentChecksum = getEquivalentChecksumForLoadableResource(repeatable, resources);
            migrations.add(new ResolvedMigrationImpl(
                    resourceName.getVersion(),
                    resourceName.getDescription(),
                    resource.getRelativePath(),
                    checksum,
                    equivalentChecksum,
                    JsonMigrationType.JSON,
                    resource.getAbsolutePathOnDisk(),
                    new JsonMigrationExecutor(resource)) {
                @Override
                public void validate() {}
            });
        }
    }
}