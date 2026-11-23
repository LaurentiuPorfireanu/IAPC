package com.example.rag.retrieval_pre.controller;

import com.example.rag.retrieval_pre.dto.FilterErrorResponse;
import com.example.rag.retrieval_pre.service.InvalidMetadataFilterException;
import com.example.rag.retrieval_pre.service.MetadataFilterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MetadataFilterService metadataFilterService;

    public GlobalExceptionHandler(MetadataFilterService metadataFilterService) {
        this.metadataFilterService = metadataFilterService;
    }

    @ExceptionHandler(InvalidMetadataFilterException.class)
    public ResponseEntity<FilterErrorResponse> handleInvalidMetadataFilter(InvalidMetadataFilterException ex) {
        log.warn("Rejecting request due to invalid metadata filter: {}", ex.getMessage());
        FilterErrorResponse body = new FilterErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Invalid Metadata Filter",
                ex.getMessage(),
                ex.getValidFields(),
                ex.getValidOperators(),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<FilterErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Rejecting request due to invalid query argument: {}", ex.getMessage());
        FilterErrorResponse body = new FilterErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                ex.getMessage(),
                metadataFilterService.getValidFields(),
                metadataFilterService.getValidOperators(),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<FilterErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        log.warn("Rejecting request due to parameter type mismatch: {}", message);
        FilterErrorResponse body = new FilterErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                message,
                metadataFilterService.getValidFields(),
                metadataFilterService.getValidOperators(),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(body);
    }
}
