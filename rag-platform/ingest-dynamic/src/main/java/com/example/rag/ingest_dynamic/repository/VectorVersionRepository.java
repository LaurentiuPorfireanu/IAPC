package com.example.rag.ingest_dynamic.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VectorVersionRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorVersionRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    public VectorVersionRepository(
            JdbcTemplate jdbcTemplate,
            @Value("${app.ingest.vector-table-name:vector_store}") String tableName) {
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid vector table name: " + tableName);
        }
        this.jdbcTemplate = jdbcTemplate;
        this.tableName = tableName;
    }

    /**
     * Marks all currently latest chunks for a given doc_id as is_latest = false.
     * Old chunks and their valid_from timestamps remain intact in the store.
     */
    public int markPreviousVersionsNotLatest(String docId) {
        String sql = """
                UPDATE %s
                SET metadata = jsonb_set(COALESCE(metadata::jsonb, '{}'::jsonb), '{is_latest}', 'false'::jsonb, true)
                WHERE metadata->>'doc_id' = ?
                  AND COALESCE((metadata->>'is_latest')::boolean, true) = true
                """.formatted(tableName);

        int updatedRows = jdbcTemplate.update(sql, docId);
        if (updatedRows > 0) {
            log.info("Marked {} previous chunk(s) as is_latest=false for doc_id={}", updatedRows, docId);
        }
        return updatedRows;
    }
    /**
     * Checks whether a specific (doc_id, version) pair already exists.
     */
    public boolean versionExists(String docId, String version) {
        String sql = """
                SELECT COUNT(*)
                FROM %s
                WHERE metadata->>'doc_id' = ?
                  AND metadata->>'version' = ?
                """.formatted(tableName);

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, docId, version);
        return count != null && count > 0;
    }

    /**
     * Counts distinct existing versions for a doc_id so we can auto-increment (v1, v2, ...)
     * when the caller does not specify an explicit version label.
     */
    public int countDistinctVersions(String docId) {
        String sql = """
                SELECT COUNT(DISTINCT metadata->>'version')
                FROM %s
                WHERE metadata->>'doc_id' = ?
                  AND metadata->>'version' IS NOT NULL
                """.formatted(tableName);

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, docId);
        return count != null ? count : 0;
    }
}