package com.example.DocuMindAI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

@SpringBootApplication
public class DocuMindAiApplication {

    private static final Logger logger = LoggerFactory.getLogger(DocuMindAiApplication.class);

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(DocuMindAiApplication.class, args);
    }

    private static void loadDotEnv() {
        File envFile = new File(".env");
        if (!envFile.exists() || !envFile.isFile()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
            String line;
            int loadedCount = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                        loadedCount++;
                    }
                }
            }
            if (loadedCount > 0) {
                logger.info("Loaded {} environment variables from .env file into application context.", loadedCount);
            }
        } catch (IOException ex) {
            logger.warn("Could not read .env file: {}", ex.getMessage());
        }
    }
}

