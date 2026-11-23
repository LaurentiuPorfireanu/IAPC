package com.example.rag.retrieval_pre.service;

import com.example.rag.retrieval_pre.config.MetadataFilterProperties;
import com.example.rag.retrieval_pre.dto.FilterCondition;
import com.example.rag.retrieval_pre.dto.FilterGroup;
import com.example.rag.retrieval_pre.dto.FilterMetadataSchemaResponse;
import com.example.rag.retrieval_pre.dto.FilterOperator;
import com.example.rag.retrieval_pre.dto.LogicalOperator;
import com.example.rag.retrieval_pre.dto.MetadataFieldType;
import com.example.rag.retrieval_pre.dto.MetadataFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MetadataFilterService {

    private static final Logger log = LoggerFactory.getLogger(MetadataFilterService.class);

    private static final Set<String> RESERVED_PARAMS = Set.of("query", "topK", "logic", "filter", "filterGroup");
    private static final Pattern BRACKET_PARAM_PATTERN = Pattern.compile("^([a-zA-Z0-9_]+)\\[([a-zA-Z]+)]$");

    private final MetadataFilterProperties properties;

    public MetadataFilterService(MetadataFilterProperties properties) {
        this.properties = properties;
    }

    /**
     * Parses and validates URL query parameters into an immutable MetadataFilter object.
     * Returns null when no filter parameters are supplied so the caller can fall through to standard retrieval.
     */
    public MetadataFilter parseAndValidate(MultiValueMap<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return null;
        }

        LogicalOperator explicitLogic = parseExplicitLogic(queryParams.get("logic"));

        List<FilterCondition> topLevelConditions = new ArrayList<>();
        List<FilterGroup> groups = new ArrayList<>();

        List<String> rawFilters = queryParams.get("filter");
        if (rawFilters != null) {
            for (String rawFilter : rawFilters) {
                topLevelConditions.add(parseColonExpression(rawFilter));
            }
        }

        List<String> rawGroups = queryParams.get("filterGroup");
        if (rawGroups != null) {
            for (String rawGroup : rawGroups) {
                groups.add(parseFilterGroup(rawGroup));
            }
        }

        for (Map.Entry<String, List<String>> entry : queryParams.entrySet()) {
            String paramName = entry.getKey();
            if (RESERVED_PARAMS.contains(paramName)) {
                continue;
            }
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) {
                throw invalidFilter("Filter parameter '" + paramName + "' must have a non-empty value");
            }
            for (String rawValue : values) {
                topLevelConditions.add(parseDirectParameter(paramName, rawValue));
            }
        }

        if (topLevelConditions.isEmpty() && groups.isEmpty()) {
            return null;
        }

        MetadataFilter result;
        if (groups.isEmpty()) {
            LogicalOperator resolvedLogic = explicitLogic != null ? explicitLogic : LogicalOperator.AND;
            result = MetadataFilter.singleGroup(resolvedLogic, topLevelConditions);
        } else {
            List<FilterGroup> allGroups = new ArrayList<>();
            if (!topLevelConditions.isEmpty()) {
                allGroups.add(new FilterGroup(LogicalOperator.AND, topLevelConditions));
            }
            allGroups.addAll(groups);
            LogicalOperator rootLogic = explicitLogic != null
                    ? explicitLogic
                    : (allGroups.size() > 1 ? LogicalOperator.OR : LogicalOperator.AND);
            result = new MetadataFilter(rootLogic, allGroups);
        }

        if (result.totalConditionCount() > properties.getMaxConditions()) {
            throw invalidFilter("Too many filter conditions (" + result.totalConditionCount()
                    + "). Maximum allowed is " + properties.getMaxConditions());
        }

        log.debug("Parsed validated MetadataFilter with {} group(s) and {} condition(s), rootOperator={}",
                result.groups().size(), result.totalConditionCount(), result.rootOperator());
        return result;
    }

    /**
     * Converts a validated MetadataFilter into a Spring AI Filter.Expression using FilterExpressionBuilder.
     * Raw user strings never reach the vector store directly.
     */
    public Filter.Expression toFilterExpression(MetadataFilter filter) {
        if (filter == null) {
            return null;
        }

        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op rootOp = null;

        for (FilterGroup group : filter.groups()) {
            FilterExpressionBuilder.Op groupOp = buildGroupOp(builder, group);
            rootOp = combine(builder, rootOp, groupOp, filter.rootOperator());
        }

        if (rootOp == null) {
            return null;
        }
        return rootOp.build();
    }

    public FilterMetadataSchemaResponse getMetadataSchema() {
        return new FilterMetadataSchemaResponse(
                properties.getAllowedFields(),
                properties.getSortedValidOperators(),
                Arrays.stream(LogicalOperator.values()).map(Enum::name).toList()
        );
    }

    public List<String> getValidFields() {
        return properties.getSortedValidFields();
    }

    public List<String> getValidOperators() {
        return properties.getSortedValidOperators();
    }

    private LogicalOperator parseExplicitLogic(List<String> logicValues) {
        if (logicValues == null || logicValues.isEmpty()) {
            return null;
        }
        if (logicValues.size() > 1) {
            throw invalidFilter("Only a single 'logic' parameter (AND or OR) is allowed");
        }
        String rawLogic = logicValues.get(0);
        if (rawLogic == null || rawLogic.isBlank()) {
            throw invalidFilter("Parameter 'logic' must be either AND or OR");
        }
        return LogicalOperator.fromString(rawLogic)
                .orElseThrow(() -> invalidFilter(
                        "Invalid logical operator '" + rawLogic + "'. Supported values are: AND, OR"));
    }

    private FilterGroup parseFilterGroup(String rawGroup) {
        if (rawGroup == null || rawGroup.isBlank()) {
            throw invalidFilter("Parameter 'filterGroup' must not be blank. Expected format: field:op:value;field2:op:value2");
        }
        String[] parts = rawGroup.split(";");
        List<FilterCondition> conditions = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                throw invalidFilter("Empty condition found in 'filterGroup'. Expected format: field:op:value;field2:op:value2");
            }
            conditions.add(parseColonExpression(part.trim()));
        }
        return new FilterGroup(LogicalOperator.AND, conditions);
    }

    private FilterCondition parseColonExpression(String rawExpression) {
        if (rawExpression == null || rawExpression.isBlank()) {
            throw invalidFilter("Filter expression must not be blank. Expected format: field:operator:value");
        }
        String[] parts = rawExpression.split(":", 3);
        if (parts.length != 3) {
            throw invalidFilter("Malformed filter expression '" + rawExpression
                    + "'. Expected format: field:operator:value");
        }
        return buildValidatedCondition(parts[0], parts[1], parts[2]);
    }

    private FilterCondition parseDirectParameter(String paramName, String rawValue) {
        Matcher matcher = BRACKET_PARAM_PATTERN.matcher(paramName);
        if (matcher.matches()) {
            String field = matcher.group(1);
            String operatorCode = matcher.group(2);
            return buildValidatedCondition(field, operatorCode, rawValue);
        }
        return buildValidatedCondition(paramName, FilterOperator.EQ.getCode(), rawValue);
    }

    private FilterCondition buildValidatedCondition(String rawField, String rawOperator, String rawValue) {
        String field = rawField == null ? "" : rawField.trim();
        MetadataFieldType fieldType = properties.getAllowedFields().get(field);
        if (fieldType == null) {
            throw invalidFilter("Invalid metadata filter field '" + field
                    + "'. Valid fields: " + properties.getSortedValidFields());
        }

        String opCode = rawOperator == null ? "" : rawOperator.trim().toLowerCase();
        FilterOperator operator = FilterOperator.fromCode(opCode)
                .filter(op -> properties.getAllowedOperators().contains(op.getCode()))
                .orElseThrow(() -> invalidFilter("Invalid filter operator '" + rawOperator
                        + "' for field '" + field + "'. Valid operators: " + properties.getSortedValidOperators()));

        if (operator.isRangeOperator() && fieldType == MetadataFieldType.BOOLEAN) {
            throw invalidFilter("Range operator '" + operator.getCode()
                    + "' is not supported for boolean field '" + field + "'");
        }

        Object typedValue = parseTypedValue(field, fieldType, operator, rawValue);
        return new FilterCondition(field, operator, typedValue);
    }

    private Object parseTypedValue(
            String field,
            MetadataFieldType fieldType,
            FilterOperator operator,
            String rawValue
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            throw invalidFilter("Filter value for field '" + field + "' must not be blank");
        }
        validateRawValueSafety(field, rawValue);

        if (operator == FilterOperator.IN) {
            String[] items = rawValue.split(",");
            List<Object> parsedItems = new ArrayList<>();
            for (String item : items) {
                String trimmedItem = item.trim();
                if (trimmedItem.isEmpty()) {
                    throw invalidFilter("Empty item in 'in' list for field '" + field + "'");
                }
                parsedItems.add(parseScalarValue(field, fieldType, trimmedItem));
            }
            if (parsedItems.isEmpty()) {
                throw invalidFilter("Operator 'in' requires at least one value for field '" + field + "'");
            }
            return List.copyOf(parsedItems);
        }

        return parseScalarValue(field, fieldType, rawValue.trim());
    }

    private Object parseScalarValue(String field, MetadataFieldType fieldType, String value) {
        return switch (fieldType) {
            case STRING -> value;
            case INTEGER -> {
                try {
                    yield Integer.parseInt(value);
                } catch (NumberFormatException ex) {
                    throw invalidFilter("Invalid integer value '" + value + "' for field '" + field + "'");
                }
            }
            case DOUBLE -> {
                try {
                    double parsed = Double.parseDouble(value);
                    if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                        throw invalidFilter("Invalid numeric value '" + value + "' for field '" + field + "'");
                    }
                    yield parsed;
                } catch (NumberFormatException ex) {
                    throw invalidFilter("Invalid numeric value '" + value + "' for field '" + field + "'");
                }
            }
            case BOOLEAN -> {
                if ("true".equalsIgnoreCase(value)) {
                    yield Boolean.TRUE;
                }
                if ("false".equalsIgnoreCase(value)) {
                    yield Boolean.FALSE;
                }
                throw invalidFilter("Invalid boolean value '" + value + "' for field '" + field
                        + "'. Expected 'true' or 'false'");
            }
        };
    }

    private void validateRawValueSafety(String field, String rawValue) {
        if (rawValue.length() > properties.getMaxValueLength()) {
            throw invalidFilter("Filter value for field '" + field + "' exceeds maximum length of "
                    + properties.getMaxValueLength() + " characters");
        }
        for (int i = 0; i < rawValue.length(); i++) {
            char ch = rawValue.charAt(i);
            if (ch < 0x20 || ch == 0x7F) {
                throw invalidFilter("Filter value for field '" + field + "' contains disallowed control characters");
            }
        }
    }

    private FilterExpressionBuilder.Op buildGroupOp(FilterExpressionBuilder builder, FilterGroup group) {
        FilterExpressionBuilder.Op groupOp = null;
        for (FilterCondition condition : group.conditions()) {
            FilterExpressionBuilder.Op conditionOp = buildConditionOp(builder, condition);
            groupOp = combine(builder, groupOp, conditionOp, group.operator());
        }
        return groupOp;
    }

    private FilterExpressionBuilder.Op buildConditionOp(
            FilterExpressionBuilder builder,
            FilterCondition condition
    ) {
        String field = condition.field();
        Object value = condition.value();
        return switch (condition.operator()) {
            case EQ -> builder.eq(field, value);
            case NE -> builder.ne(field, value);
            case GT -> builder.gt(field, value);
            case GTE -> builder.gte(field, value);
            case LT -> builder.lt(field, value);
            case LTE -> builder.lte(field, value);
            case IN -> builder.in(field, ((List<?>) value).toArray());
        };
    }

    private FilterExpressionBuilder.Op combine(
            FilterExpressionBuilder builder,
            FilterExpressionBuilder.Op left,
            FilterExpressionBuilder.Op right,
            LogicalOperator logicalOperator
    ) {
        if (left == null) {
            return right;
        }
        return logicalOperator == LogicalOperator.OR
                ? builder.or(left, right)
                : builder.and(left, right);
    }

    private InvalidMetadataFilterException invalidFilter(String message) {
        log.warn("Metadata filter validation failed: {}", message);
        return new InvalidMetadataFilterException(
                message,
                properties.getSortedValidFields(),
                properties.getSortedValidOperators()
        );
    }
}
