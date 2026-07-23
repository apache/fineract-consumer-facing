/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CqrsArchitectureTest {

    private static final String BASE_PACKAGE = "org.apache.fineract.consumer";
    private static final Pattern FEATURE_SIDE_PACKAGE = Pattern
            .compile("^" + Pattern.quote(BASE_PACKAGE) + "\\.([^.]+)\\.(command|query)(\\.|$)");

    private static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(BASE_PACKAGE);

    private static final Set<String> FEATURES = discoverFeatures();

    private static Set<String> discoverFeatures() {
        return CLASSES.stream().map(JavaClass::getPackageName).map(FEATURE_SIDE_PACKAGE::matcher).filter(Matcher::find)
                .map(matcher -> matcher.group(1)).filter(feature -> !"infrastructure".equals(feature)).collect(Collectors.toSet());
    }

    @Test
    void discoversFeatureModules() {
        assertTrue(FEATURES.containsAll(Set.of("user", "registration", "identity")),
                "feature discovery is broken; found only: " + FEATURES);
    }

    @Test
    void commandSideMustNotDependOnSameFeatureQuerySide() {
        for (String feature : FEATURES) {
            noClasses().that().resideInAPackage(sidePackage(feature, "command")).should().dependOnClassesThat()
                    .resideInAPackage(sidePackage(feature, "query")).allowEmptyShould(true).check(CLASSES);
        }
    }

    @Test
    void querySideMustNotDependOnSameFeatureCommandSide() {
        for (String feature : FEATURES) {
            noClasses().that().resideInAPackage(sidePackage(feature, "query")).should().dependOnClassesThat()
                    .resideInAPackage(sidePackage(feature, "command")).allowEmptyShould(true).check(CLASSES);
        }
    }

    private static String sidePackage(String feature, String side) {
        return BASE_PACKAGE + "." + feature + "." + side + "..";
    }
}
