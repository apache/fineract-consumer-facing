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

package org.apache.fineract.consumer.infrastructure.fineractclient.configs;

import com.fasterxml.jackson.annotation.JsonInclude;
import feign.codec.Encoder;
import java.util.stream.Stream;
import org.apache.fineract.consumer.infrastructure.fineractclient.interceptors.FineractBasicAuthInterceptor;
import org.apache.fineract.consumer.infrastructure.fineractclient.interceptors.FineractIdempotencyKeyInterceptor;
import org.apache.fineract.consumer.infrastructure.fineractclient.interceptors.FineractTenantHeaderInterceptor;
import org.apache.fineract.consumer.infrastructure.idempotency.service.IdempotencyKeyHolder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;
import org.springframework.cloud.openfeign.support.HttpMessageConverterCustomizer;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.context.annotation.RequestScope;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableConfigurationProperties(FineractClientProperties.class)
public class FineractClientConfig {

    @Bean
    public FineractBasicAuthInterceptor fineractBasicAuthInterceptor(FineractClientProperties properties) {
        return new FineractBasicAuthInterceptor(properties);
    }

    @Bean
    public FineractTenantHeaderInterceptor fineractTenantHeaderInterceptor(FineractClientProperties properties) {
        return new FineractTenantHeaderInterceptor(properties);
    }

    @Bean
    @RequestScope
    public IdempotencyKeyHolder idempotencyKeyHolder() {
        return new IdempotencyKeyHolder();
    }

    @Bean
    public FineractIdempotencyKeyInterceptor fineractIdempotencyKeyInterceptor(
            IdempotencyKeyHolder idempotencyKeyHolder) {
        return new FineractIdempotencyKeyInterceptor(idempotencyKeyHolder);
    }

    @Bean
    public Encoder fineractFeignEncoder(JsonMapper jsonMapper) {
        JsonMapper nonEmptyMapper = jsonMapper.rebuild()
                .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .build();
        HttpMessageConverter<?> jsonConverter = new JacksonJsonHttpMessageConverter(nonEmptyMapper);
        FeignHttpMessageConverters converters = new FeignHttpMessageConverters(singletonProvider(jsonConverter),
                FineractClientConfig.<HttpMessageConverterCustomizer>emptyProvider());
        return new SpringEncoder(singletonProvider(converters));
    }

    private static <T> ObjectProvider<T> singletonProvider(T instance) {
        return new ObjectProvider<>() {

            @Override
            public Stream<T> stream() {
                return Stream.of(instance);
            }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return new ObjectProvider<>() {

            @Override
            public Stream<T> stream() {
                return Stream.empty();
            }
        };
    }
}
