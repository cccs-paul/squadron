package com.squadron.platform.config;

import com.squadron.common.security.BaseSecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig extends BaseSecurityConfig {

    @Override
    protected String[] additionalPermitAllPaths() {
        return new String[]{"/api/platforms/webhooks/**"};
    }
}
