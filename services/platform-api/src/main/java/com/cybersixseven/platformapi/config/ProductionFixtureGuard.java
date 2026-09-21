package com.cybersixseven.platformapi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionFixtureGuard implements ApplicationRunner {

    private final boolean fixturesEnabled;
    private final boolean oauthFixtureEnabled;

    public ProductionFixtureGuard(
            @Value("${app.fixtures.enabled}") boolean fixturesEnabled,
            @Value("${app.oauth.fixture.enabled}") boolean oauthFixtureEnabled) {
        this.fixturesEnabled = fixturesEnabled;
        this.oauthFixtureEnabled = oauthFixtureEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (fixturesEnabled || oauthFixtureEnabled) {
            throw new IllegalStateException("fixtures cannot be enabled in production");
        }
    }
}
