package org.av360.maverick.graph.store.postgres.config;/*
 * Copyright (c) 2024.
 *
 *  Licensed under the EUPL, Version 1.2 or – as soon they will be approved by the
 *  European Commission - subsequent versions of the EUPL (the "Licence");
 *
 *  You may not use this work except in compliance with the Licence.
 *  You may obtain a copy of the Licence at:
 *
 *  https://joinup.ec.europa.eu/software/page/eupl5
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed on an "AS IS" basis,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the Licence for the specific language governing permissions and limitations under the Licence.
 *
 */

import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@Configuration
@EnableR2dbcAuditing
@EnableR2dbcRepositories(basePackages = "org.av360.maverick.graph.store.postgres.repositories")
public class DatabaseConfig extends AbstractR2dbcConfiguration {

    private static final String DATABASE_DRIVER = "postgresql";
    private static final String DATABASE_HOST = "localhost";
    private static final int DATABASE_PORT = 5432;  // optional, default 5432
    private static final String DATABASE_NAME = "test";  // optional

    @Value("${spring.r2dbc.url}")
    private String url;
    @Value("${spring.r2dbc.username}")
    private String username;
    @Value("${spring.r2dbc.password}")
    private String password;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        ConnectionFactoryOptions options = createConnectionFactoryOptions();
        return ConnectionFactories.get(options);
    }

    private ConnectionFactoryOptions createConnectionFactoryOptions() {
        return ConnectionFactoryOptions.builder()
                .option(DRIVER, DATABASE_DRIVER)
                .option(HOST, DATABASE_HOST)
                .option(PORT, DATABASE_PORT)
                .option(USER, username)
                .option(PASSWORD, password)
                .option(DATABASE, DATABASE_NAME)
                .build();
    }
}