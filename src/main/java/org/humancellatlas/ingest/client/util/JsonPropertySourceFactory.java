package org.humancellatlas.ingest.client.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertySourceFactory;

import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class JsonPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        if (resource == null) {
            throw new IllegalArgumentException("Resource must not be null");
        }

        Properties properties = loadJsonProperties(resource.getResource());

        return new PropertiesPropertySource(name, properties);
    }

    private Properties loadJsonProperties(Resource resource) throws IOException {
        JsonParser parser = JsonParserFactory.getJsonParser();
        Map<String, Object> jsonMap = parser.parseMap(convertResourceToString(resource));
        Properties properties = new Properties();
        properties.putAll(jsonMap);
        return properties;
    }

    private static String convertResourceToString(Resource resource) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return bufferedReader.lines()
                    .collect(Collectors.joining(System.lineSeparator()));
        }
    }
}
