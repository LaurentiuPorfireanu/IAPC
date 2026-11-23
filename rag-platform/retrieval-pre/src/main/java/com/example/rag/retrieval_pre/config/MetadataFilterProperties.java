package com.example.rag.retrieval_pre.config;

import com.example.rag.retrieval_pre.dto.MetadataFieldType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "app.filter")
public class MetadataFilterProperties {

    private int defaultTopK = 5;
    private int maxTopK = 50;
    private int maxConditions = 20;
    private int maxValueLength = 256;
    private Map<String, MetadataFieldType> allowedFields = new LinkedHashMap<>();
    private Set<String> allowedOperators = new LinkedHashSet<>();

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }

    public int getMaxConditions() {
        return maxConditions;
    }

    public void setMaxConditions(int maxConditions) {
        this.maxConditions = maxConditions;
    }

    public int getMaxValueLength() {
        return maxValueLength;
    }

    public void setMaxValueLength(int maxValueLength) {
        this.maxValueLength = maxValueLength;
    }

    public Map<String, MetadataFieldType> getAllowedFields() {
        return allowedFields;
    }

    public void setAllowedFields(Map<String, MetadataFieldType> allowedFields) {
        this.allowedFields = allowedFields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(allowedFields);
    }

    public Set<String> getAllowedOperators() {
        return allowedOperators;
    }

    public void setAllowedOperators(Set<String> allowedOperators) {
        this.allowedOperators = allowedOperators == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allowedOperators);
    }

    public List<String> getSortedValidFields() {
        return allowedFields.keySet().stream().sorted().toList();
    }

    public List<String> getSortedValidOperators() {
        return allowedOperators.stream().map(String::toLowerCase).sorted().toList();
    }
}
