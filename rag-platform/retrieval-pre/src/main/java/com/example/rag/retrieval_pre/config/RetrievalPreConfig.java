package com.example.rag.retrieval_pre.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MetadataFilterProperties.class)
public class RetrievalPreConfig {
}
