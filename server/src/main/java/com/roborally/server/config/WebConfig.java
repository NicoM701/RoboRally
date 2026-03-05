package com.roborally.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve game assets from the gameResources directory
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("file:../gameResources/");
        
        // Serve client files
        registry.addResourceHandler("/**")
                .addResourceLocations("file:../client/");
    }
}
