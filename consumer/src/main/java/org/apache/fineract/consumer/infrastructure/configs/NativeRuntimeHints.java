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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.infrastructure.configs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Reflection the native image needs that neither Spring AOT nor the GraalVM reachability metadata repository
 * provides. Found by running the Cucumber suite against the JVM build under the native-image tracing agent.
 * Has no effect on the JVM.
 */
public class NativeRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Spring Boot's {@code TomcatWebServer} reads and writes connector settings through Tomcat's
     * {@code IntrospectionUtils}, which calls public getters and setters such as
     * {@code AbstractProtocol.getProperty(String)} reflectively. The metadata repository only covers Tomcat 10.0.
     */
    private static final List<String> TOMCAT_CONNECTOR_TYPES = List.of(
            "org.apache.coyote.AbstractProtocol",
            "org.apache.coyote.http11.AbstractHttp11Protocol",
            "org.apache.coyote.http11.Http11NioProtocol",
            "org.apache.tomcat.util.net.AbstractEndpoint",
            "org.apache.tomcat.util.net.AbstractJsseEndpoint",
            "org.apache.tomcat.util.net.NioEndpoint");

    /**
     * Liquibase computes changeset checksums by serializing each change through JavaBeans introspection of the
     * {@code liquibase.change} model. Without these hints the native image applies the same DDL but computes
     * different checksums, so it refuses a database migrated by the JVM build (and the reverse).
     */
    private static final String LIQUIBASE_CHANGE_MODEL = "classpath*:liquibase/change/**/*.class";

    /**
     * Spring Security creates its Jackson modules, deserializers and mixins reflectively when
     * {@code JdbcOAuth2AuthorizationService} builds its mapper, and skips any it cannot instantiate without
     * failing. In a native image the open banking authorization flow then cannot read back the attributes it
     * stored, so registered TPPs cannot complete authorization.
     */
    private static final String SPRING_SECURITY_JACKSON = "classpath*:org/springframework/security/**/jackson/*.class";

    /**
     * Caffeine picks a generated cache class (SSW) and node class (PSW) for the builder options in use, today only
     * {@code expireAfterWrite} (in {@code OwnedAccountsCache} and {@code BusinessDateFineractClientImpl}), and binds
     * them and its queue fields through static fields and VarHandles. The metadata repository's Caffeine entry
     * predates 3.2. Adding builder options (a size bound, refresh, weak keys) selects different generated classes,
     * which must then be added here.
     */
    private static final List<String> CAFFEINE_GENERATED_TYPES = List.of(
            "com.github.benmanes.caffeine.cache.SSW",
            "com.github.benmanes.caffeine.cache.PSW",
            "com.github.benmanes.caffeine.cache.PS",
            "com.github.benmanes.caffeine.cache.BoundedLocalCache",
            "com.github.benmanes.caffeine.cache.BLCHeader$DrainStatusRef",
            "com.github.benmanes.caffeine.cache.BaseMpscLinkedArrayQueueColdProducerFields",
            "com.github.benmanes.caffeine.cache.BaseMpscLinkedArrayQueueConsumerFields",
            "com.github.benmanes.caffeine.cache.BaseMpscLinkedArrayQueueProducerFields");

    /**
     * Spring Security and Spring Authorization Server types accessed reflectively on the open banking flow:
     * private fields its Jackson deserializers set when reading a stored authorization back, {@code getValue()}
     * on the value types it serializes, the authorization endpoint's internal validator wiring, and the
     * authentication failure event it instantiates.
     */
    private static final List<String> SPRING_SECURITY_REFLECTIVE_TYPES = List.of(
            "org.springframework.security.authentication.AbstractAuthenticationToken",
            "org.springframework.security.authentication.UsernamePasswordAuthenticationToken",
            "org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent",
            "org.springframework.security.oauth2.core.AuthorizationGrantType",
            "org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest",
            "org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationResponseType",
            "org.springframework.security.oauth2.server.authorization.authentication."
                    + "OAuth2AuthorizationCodeRequestAuthenticationProvider",
            "org.springframework.security.oauth2.server.authorization.authentication."
                    + "OAuth2AuthorizationCodeRequestAuthenticationToken",
            "org.springframework.security.oauth2.server.authorization.web.OAuth2AuthorizationEndpointFilter");

    /**
     * JDK collection types that Spring Authorization Server writes as Jackson type ids ({@code "@class"}) into
     * {@code oauth2_authorization} and resolves by name when reading them back. A native image can only look up
     * classes by name if they are registered; JDK-private ones such as {@code Collections$UnmodifiableMap} are not
     * by default, so the type-id allowlist rejects them.
     */
    private static final List<String> SERIALIZED_JDK_COLLECTION_TYPES = List.of(
            "java.util.Collections$UnmodifiableMap",
            "java.util.Collections$UnmodifiableSet",
            "java.util.Collections$UnmodifiableList",
            "java.util.Collections$UnmodifiableRandomAccessList",
            "java.util.Collections$UnmodifiableCollection",
            "java.util.Collections$SingletonList",
            "java.util.ArrayList",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.LinkedHashMap");

    /** Array types Hibernate and Spring Data instantiate reflectively for the entity attribute types in use. */
    private static final List<Class<?>> REFLECTIVE_ARRAY_TYPES = List.of(UUID[].class, Long[].class, String[].class);

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        TOMCAT_CONNECTOR_TYPES.forEach(type -> hints.reflection()
                .registerType(TypeReference.of(type), MemberCategory.INVOKE_PUBLIC_METHODS));
        liquibaseChangeModel(classLoader).forEach(type -> hints.reflection().registerType(TypeReference.of(type),
                MemberCategory.INVOKE_PUBLIC_METHODS, MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS));
        springSecurityJackson(classLoader).forEach(type -> hints.reflection().registerType(TypeReference.of(type),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS));
        CAFFEINE_GENERATED_TYPES.forEach(type -> hints.reflection().registerType(TypeReference.of(type),
                MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        SPRING_SECURITY_REFLECTIVE_TYPES.forEach(type -> hints.reflection().registerType(TypeReference.of(type),
                MemberCategory.ACCESS_DECLARED_FIELDS, MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        SERIALIZED_JDK_COLLECTION_TYPES.forEach(type -> hints.reflection().registerType(TypeReference.of(type)));
        REFLECTIVE_ARRAY_TYPES.forEach(type -> hints.reflection().registerType(type));
    }

    static List<String> liquibaseChangeModel(ClassLoader classLoader) {
        return classesMatching(LIQUIBASE_CHANGE_MODEL, classLoader);
    }

    static List<String> springSecurityJackson(ClassLoader classLoader) {
        return classesMatching(SPRING_SECURITY_JACKSON, classLoader);
    }

    // Runs during AOT processing on the JVM, where the classpath can be scanned.
    private static List<String> classesMatching(String pattern, ClassLoader classLoader) {
        try {
            Resource[] classFiles = new PathMatchingResourcePatternResolver(classLoader).getResources(pattern);
            return Arrays.stream(classFiles)
                    .map(classFile -> className(classFile, packageRoot(pattern)))
                    .filter(name -> !name.endsWith("package-info"))
                    .distinct()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot scan " + pattern + " for native hints", e);
        }
    }

    private static String packageRoot(String pattern) {
        String path = pattern.substring("classpath*:".length());
        return path.substring(0, path.indexOf('/') + 1);
    }

    private static String className(Resource classFile, String packageRoot) {
        try {
            String url = classFile.getURL().toString();
            String path = url.substring(url.lastIndexOf("/" + packageRoot) + 1);
            return path.substring(0, path.length() - ".class".length()).replace('/', '.');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
