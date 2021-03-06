/*
 * Copyright 2018 Mark Adamcin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.adamcin.oakpal.core;

import aQute.bnd.annotation.ConsumerType;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.jetbrains.annotations.NotNull;

import javax.json.JsonObject;
import java.util.Collection;
import java.util.function.Predicate;

import static net.adamcin.oakpal.core.JavaxJson.obj;
import static net.adamcin.oakpal.core.ReportMapper.KEY_DESCRIPTION;
import static net.adamcin.oakpal.core.ReportMapper.KEY_PACKAGES;
import static net.adamcin.oakpal.core.ReportMapper.KEY_SEVERITY;

/**
 * Report type for validations.
 */
@ConsumerType
public interface Violation extends JavaxJson.ObjectConvertible {

    /**
     * Levels of severity for violations detected during package scans.
     */
    enum Severity {
        /**
         * Unlikely to disrupt application functionality. Appropriate for reporting violations of
         * code or style conventions, or inconsistency between modes of installation.
         */
        MINOR(2),

        /**
         * Likely to be the source of component instability. Appropriate for importer errors, mistaken
         * assumptions about root path dependencies or namespaces, or failures related to unit testing of
         * application packages.
         */
        MAJOR(1),

        /**
         * Likely to be the source of platform instability. Appropriate for reporting cross-package filter
         * overlap, destructive ACL handling modes, destruction of authorable content, or security violations.
         */
        SEVERE(0);

        private final int ordinal;

        Severity(int ordinal) {
            this.ordinal = ordinal;
        }

        /**
         * Runtime throwing function to lookup severity codes by name.
         *
         * @param name the severity level name
         * @return the associated severity level
         */
        public static Severity byName(final @NotNull String name) {
            for (Severity value : values()) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Unknown severity level: " + name);
        }

        public boolean isLessSevereThan(Severity other) {
            return this.ordinal > other.ordinal;
        }

        public Predicate<Severity> meetsMinimumSeverity() {
            return other -> !other.isLessSevereThan(this);
        }

        public Severity maxSeverity(final @NotNull Severity other) {
            return this.isLessSevereThan(other) ? other : this;
        }
    }

    /**
     * Describe the severity of the violation.
     *
     * @return the severity of the violation
     */
    Severity getSeverity();

    /**
     * Provides a list of one or more Packages responsible for the violation.
     *
     * @return a list of package IDs responsible for the violation.
     */
    Collection<PackageId> getPackages();

    /**
     * Describes the nature of the violation.
     *
     * @return the description
     */
    String getDescription();

    /**
     * Serializes the Violation to a JsonObject.
     *
     * @return the json representation of the violation
     */
    @Override
    default JsonObject toJson() {
        return obj()
                .key(KEY_SEVERITY).opt(this.getSeverity())
                .key(KEY_DESCRIPTION).opt(this.getDescription())
                .key(KEY_PACKAGES).opt(this.getPackages()).get();
    }
}
