package com.civicsphere.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configure view mappings for clean frontend routes.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/citizen/login").setViewName("forward:/citizen/login.html");
        registry.addViewController("/citizen/register").setViewName("forward:/register.html");
        registry.addViewController("/citizen/dashboard").setViewName("forward:/citizen/dashboard.html");
        registry.addViewController("/admin/login").setViewName("forward:/admin/login.html");
        registry.addViewController("/admin/dashboard").setViewName("forward:/admin/dashboard.html");
    }
}

