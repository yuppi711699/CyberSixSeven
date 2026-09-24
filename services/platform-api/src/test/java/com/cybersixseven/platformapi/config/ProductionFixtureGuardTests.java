package com.cybersixseven.platformapi.config;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class ProductionFixtureGuardTests {

    @Test
    void productionRejectsFixtureFlags() {
        ProductionFixtureGuard guard = new ProductionFixtureGuard(true, false);
        assertThrows(IllegalStateException.class, () -> guard.run(new DefaultApplicationArguments()));
        ProductionFixtureGuard oauth = new ProductionFixtureGuard(false, true);
        assertThrows(IllegalStateException.class, () -> oauth.run(new DefaultApplicationArguments()));
    }

    @Test
    void productionAcceptsDisabledFlags() {
        new ProductionFixtureGuard(false, false).run(new DefaultApplicationArguments());
    }
}
