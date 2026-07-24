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

import java.util.stream.Stream;
import org.springframework.modulith.core.ApplicationModuleDetectionStrategy;
import org.springframework.modulith.core.ApplicationModuleInformation;
import org.springframework.modulith.core.JavaPackage;
import org.springframework.modulith.core.NamedInterfaces;

public class ConsumerModuleDetectionStrategy implements ApplicationModuleDetectionStrategy {

    @Override
    public Stream<JavaPackage> getModuleBasePackages(JavaPackage basePackage) {
        return ApplicationModuleDetectionStrategy.directSubPackage().getModuleBasePackages(basePackage);
    }

    private static final String INFRASTRUCTURE_MODULE_NAME = "infrastructure";

    @Override
    public NamedInterfaces detectNamedInterfaces(JavaPackage moduleBasePackage, ApplicationModuleInformation information) {
        if (moduleBasePackage.getLocalName().equals(INFRASTRUCTURE_MODULE_NAME)) {
            return NamedInterfaces.forOpen(moduleBasePackage);
        }
        return NamedInterfaces.builder(moduleBasePackage).recursive()
                .matching("command.data", "command.service", "query.data", "query.service")
                .build();
    }
}
