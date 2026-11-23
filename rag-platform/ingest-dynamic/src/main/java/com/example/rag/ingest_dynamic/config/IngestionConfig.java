package com.example.rag.ingest_dynamic.config;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IngestionConfig {

    @Bean
    public TokenTextSplitter tokenTextSplitter(
            @Value("${app.ingest.chunk-size:800}") int chunkSize,
            @Value("${app.ingest.min-chunk-size-chars:350}") int minChunkSizeChars) {
        return TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(minChunkSizeChars)
                .withMinChunkLengthToEmbed(5)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();
    }
}