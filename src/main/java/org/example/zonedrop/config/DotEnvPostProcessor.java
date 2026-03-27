package org.example.zonedrop.config;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class DotEnvPostProcessor implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Path envFile = Paths.get(".env");
        if (!Files.exists(envFile)) return;

        try {
            Map<String, Object> props = new HashMap<>();
            for (String line : Files.readAllLines(envFile)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int idx = line.indexOf('=');
                if (idx < 0) continue;
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                props.put(key, value);
            }
            ConfigurableEnvironment environment = event.getEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("dotenvProperties", props));
        } catch (IOException e) {
            // silently skip if .env is unreadable
        }
    }
}
