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


import com.amazonaws.util.StringUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * migrationUnquire combine by MigrationType and MigrationVersion.
 *
 * @author liulili
 * @since 2022/4/18 9:28
 */
@Builder
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public final class MigrationVersionScript implements Comparable<MigrationVersionScript> {

    private MigrationVersion migrationVersion;

    private String script;

    @Override
    public int compareTo(MigrationVersionScript o) {
        MigrationVersion migrationVersion1 = this.getMigrationVersion();
        MigrationVersion migrationVersion2 = o.getMigrationVersion();
        String script1 = this.getScript();
        String script2 = o.getScript();
        int compare;
        if (migrationVersion1 == null) {
            compare = migrationVersion2 == null? 0:-1;
        } else {
            compare = migrationVersion1.compareTo(migrationVersion2);
        }

        if (compare == 0) {
            return StringUtils.compare(script1, script2);
        }
        return compare;
    }
}
