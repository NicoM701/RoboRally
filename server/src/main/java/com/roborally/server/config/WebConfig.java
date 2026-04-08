package com.roborally.server.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path workingDir = Paths.get("").toAbsolutePath().normalize();

        // Support launching from repo root, the server module, or build output directories.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(resolveFileResourceLocations(
                        workingDir,
                        "gameResources",
                        "../gameResources",
                        "../../gameResources",
                        "../../../gameResources"));

        registry.addResourceHandler("/**")
                .addResourceLocations(resolveFileResourceLocations(
                        workingDir,
                        "client",
                        "../client",
                        "../../client"));
    }

    static String[] resolveFileResourceLocations(Path baseDir, String... relativePaths) {
        return Arrays.stream(relativePaths)
                .map(baseDir::resolve)
                .map(Path::normalize)
                .map(path -> {
                    String uri = path.toUri().toString();
                    return uri.endsWith("/") ? uri : uri + "/";
                })
                .toArray(String[]::new);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
