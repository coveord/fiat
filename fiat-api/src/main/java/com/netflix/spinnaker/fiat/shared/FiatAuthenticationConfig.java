/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.fiat.shared;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakewharton.retrofit.Ok3Client;
import com.netflix.spinnaker.config.DefaultServiceEndpoint;
import com.netflix.spinnaker.config.ErrorConfiguration;
import com.netflix.spinnaker.config.okhttp3.OkHttpClientProvider;
import com.netflix.spinnaker.kork.web.exceptions.ExceptionMessageDecorator;
import com.netflix.spinnaker.okhttp.SpinnakerRequestInterceptor;
import com.netflix.spinnaker.retrofit.Slf4jRetrofitLogger;
import lombok.Setter;
import lombok.val;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import retrofit.Endpoints;
import retrofit.RestAdapter;
import retrofit.converter.JacksonConverter;

@Import(ErrorConfiguration.class)
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Configuration
@EnableConfigurationProperties(FiatClientConfigurationProperties.class)
@ComponentScan("com.netflix.spinnaker.fiat.shared")
public class FiatAuthenticationConfig {

  @Autowired(required = false)
  @Setter
  private RestAdapter.LogLevel retrofitLogLevel = RestAdapter.LogLevel.BASIC;

  @Bean
  @ConditionalOnMissingBean(FiatService.class) // Allows for override
  public FiatService fiatService(
      FiatClientConfigurationProperties fiatConfigurationProperties,
      SpinnakerRequestInterceptor interceptor,
      OkHttpClientProvider okHttpClientProvider) {
    // New role providers break deserialization if this is not enabled.
    val objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    OkHttpClient okHttpClient =
        okHttpClientProvider.getClient(
            new DefaultServiceEndpoint("fiat", fiatConfigurationProperties.getBaseUrl()));

    return new RestAdapter.Builder()
        .setEndpoint(Endpoints.newFixedEndpoint(fiatConfigurationProperties.getBaseUrl()))
        .setRequestInterceptor(interceptor)
        .setClient(new Ok3Client(okHttpClient))
        .setConverter(new JacksonConverter(objectMapper))
        .setLogLevel(retrofitLogLevel)
        .setLog(new Slf4jRetrofitLogger(FiatService.class))
        .build()
        .create(FiatService.class);
  }

  /**
   * When enabled, this authenticates the {@code X-SPINNAKER-USER} HTTP header using permissions
   * obtained from {@link FiatPermissionEvaluator#getPermission(String)}. This feature is part of a
   * larger effort to adopt standard Spring Security APIs rather than using Fiat directly where
   * possible.
   */
  @ConditionalOnProperty("services.fiat.granted-authorities.enabled")
  @Bean
  AuthenticationConverter fiatAuthenticationFilter(FiatPermissionEvaluator permissionEvaluator) {
    return new FiatAuthenticationConverter(permissionEvaluator);
  }

  /**
   * Provides the previous behavior of using PreAuthenticatedAuthenticationToken with no granted
   * authorities to indicate an authenticated user or an AnonymousAuthenticationToken with an
   * "ANONYMOUS" role for anonymous authenticated users.
   */
  @ConditionalOnMissingBean
  @Bean
  AuthenticationConverter defaultAuthenticationConverter() {
    return new AuthenticatedRequestAuthenticationConverter();
  }

  @Bean
  FiatWebSecurityConfigurerAdapter fiatSecurityConfig(
      FiatStatus fiatStatus, AuthenticationConverter authenticationConverter) {
    return new FiatWebSecurityConfigurerAdapter(fiatStatus, authenticationConverter);
  }

  @Bean
  @Order(HIGHEST_PRECEDENCE)
  FiatAccessDeniedExceptionHandler fiatAccessDeniedExceptionHandler(
      ExceptionMessageDecorator exceptionMessageDecorator) {
    return new FiatAccessDeniedExceptionHandler(exceptionMessageDecorator);
  }

  private static class FiatWebSecurityConfigurerAdapter extends WebSecurityConfigurerAdapter {
    private final FiatStatus fiatStatus;
    private final AuthenticationConverter authenticationConverter;

    private FiatWebSecurityConfigurerAdapter(
        FiatStatus fiatStatus, AuthenticationConverter authenticationConverter) {
      super(true);
      this.fiatStatus = fiatStatus;
      this.authenticationConverter = authenticationConverter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      http.servletApi()
          .and()
          .exceptionHandling()
          .and()
          .anonymous()
          .and()
          .addFilterBefore(
              new FiatAuthenticationFilter(fiatStatus, authenticationConverter),
              AnonymousAuthenticationFilter.class);
    }
  }
}
