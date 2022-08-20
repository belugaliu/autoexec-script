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
package org.flywaydb.core.internal.resolver;

import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.resolver.ResolvedMigration;
import org.flywaydb.core.extensibility.MigrationType;

import java.util.Comparator;

public class ResolvedMigrationComparator implements Comparator<ResolvedMigration> {
    /**
     * change compare by type，version and script three attribute.
     * @param o1 the first object to be compared.
     * @param o2 the second object to be compared.
     * @return compare result. -1 o1 smaller than o2, 0 equals,1 o1 bigger than o2
     * @author liull
     */
    @Override
    public int compare(ResolvedMigration o1, ResolvedMigration o2) {
        MigrationVersion o1Version = o1.getVersion();
        MigrationVersion o2Version = o2.getVersion();
        MigrationType o1MgrationType = o1.getType();
        MigrationType o2MigrationType = o2.getType();
        if (o1MgrationType != o2MigrationType) {
            return o1MgrationType.toString().compareTo(o2MigrationType.toString());
        }
        if (o1Version == null) {
            return -1;
        }
        int vc = o1Version.compareTo(o2Version);
        if (vc != 0) {
            return vc;
        }
        return o1.getScript().compareTo(o2.getScript());
    }
}