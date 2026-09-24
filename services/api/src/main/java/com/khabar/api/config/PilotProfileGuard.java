package com.khabar.api.config;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Prevents a controlled pilot from accidentally starting with demo identities or development helpers. */
@Component
@Profile("pilot")
public class PilotProfileGuard implements EnvironmentAware, org.springframework.beans.factory.InitializingBean {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validate(environment);
    }

    static void validate(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local", "demo"))) {
            throw new IllegalStateException("The pilot profile cannot be combined with local or demo profiles.");
        }
    }
}
