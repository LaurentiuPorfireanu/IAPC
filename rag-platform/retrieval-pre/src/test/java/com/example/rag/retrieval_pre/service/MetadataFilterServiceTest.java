package com.example.rag.retrieval_pre.service;

import com.example.rag.retrieval_pre.config.MetadataFilterProperties;
import com.example.rag.retrieval_pre.dto.FilterCondition;
import com.example.rag.retrieval_pre.dto.FilterOperator;
import com.example.rag.retrieval_pre.dto.LogicalOperator;
import com.example.rag.retrieval_pre.dto.MetadataFieldType;
import com.example.rag.retrieval_pre.dto.MetadataFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetadataFilterServiceTest {

    private MetadataFilterService service;

    @BeforeEach
    void setUp() {
        MetadataFilterProperties properties = new MetadataFilterProperties();
        properties.setDefaultTopK(5);
        properties.setMaxTopK(50);
        properties.setMaxConditions(5);
        properties.setMaxValueLength(100);

        Map<String, MetadataFieldType> fields = new LinkedHashMap<>();
        fields.put("doc_id", MetadataFieldType.STRING);
        fields.put("version", MetadataFieldType.STRING);
        fields.put("source", MetadataFieldType.STRING);
        fields.put("source_url", MetadataFieldType.STRING);
        fields.put("page", MetadataFieldType.INTEGER);
        fields.put("chunk_index", MetadataFieldType.INTEGER);
        fields.put("is_latest", MetadataFieldType.BOOLEAN);
        fields.put("org_unit", MetadataFieldType.STRING);
        fields.put("security_clearance", MetadataFieldType.STRING);
        properties.setAllowedFields(fields);
        properties.setAllowedOperators(Set.of("eq", "ne", "gt", "gte", "lt", "lte", "in"));

        service = new MetadataFilterService(properties);
    }

    @Test
    void parseAndValidate_NullOrEmptyParams_ReturnsNullForStandardFlow() {
        assertNull(service.parseAndValidate(null));
        assertNull(service.parseAndValidate(new LinkedMultiValueMap<>()));
        assertNull(service.toFilterExpression(null));
    }

    @Test
    void parseAndValidate_OnlyQueryAndTopK_ReturnsNullForStandardFlow() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "compliance audit policy");
        params.add("topK", "5");

        assertNull(service.parseAndValidate(params));
    }

    @Test
    void parseAndValidate_MultiVariableAndFilter_BuildsValidatedObjectAndExpression() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "policy requirements");
        params.add("filter", "source:eq:handbook.pdf");
        params.add("filter", "page:gte:2");
        params.add("filter", "is_latest:eq:true");
        params.add("logic", "AND");

        MetadataFilter filter = service.parseAndValidate(params);

        assertNotNull(filter);
        assertEquals(LogicalOperator.AND, filter.rootOperator());
        assertEquals(1, filter.groups().size());
        assertEquals(3, filter.totalConditionCount());

        List<FilterCondition> conditions = filter.groups().get(0).conditions();
        assertEquals(new FilterCondition("source", FilterOperator.EQ, "handbook.pdf"), conditions.get(0));
        assertEquals(new FilterCondition("page", FilterOperator.GTE, 2), conditions.get(1));
        assertEquals(new FilterCondition("is_latest", FilterOperator.EQ, Boolean.TRUE), conditions.get(2));

        Filter.Expression expression = service.toFilterExpression(filter);
        assertNotNull(expression);
        assertEquals(Filter.ExpressionType.AND, expression.type());
    }

    @Test
    void parseAndValidate_MultiVariableOrFilter_BuildsOrExpression() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "doc_id:eq:doc-001");
        params.add("filter", "doc_id:eq:doc-002");
        params.add("logic", "OR");

        MetadataFilter filter = service.parseAndValidate(params);

        assertNotNull(filter);
        assertEquals(LogicalOperator.OR, filter.rootOperator());
        assertEquals(2, filter.totalConditionCount());

        Filter.Expression expression = service.toFilterExpression(filter);
        assertNotNull(expression);
        assertEquals(Filter.ExpressionType.OR, expression.type());
    }

    @Test
    void parseAndValidate_GroupedAndOrFilters_BuildsNestedDisjunctiveExpression() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filterGroup", "org_unit:eq:unit-a;page:gte:2");
        params.add("filterGroup", "org_unit:eq:unit-b;is_latest:eq:true");

        MetadataFilter filter = service.parseAndValidate(params);

        assertNotNull(filter);
        assertEquals(LogicalOperator.OR, filter.rootOperator());
        assertEquals(2, filter.groups().size());
        assertEquals(LogicalOperator.AND, filter.groups().get(0).operator());
        assertEquals(LogicalOperator.AND, filter.groups().get(1).operator());

        Filter.Expression expression = service.toFilterExpression(filter);
        assertNotNull(expression);
        assertEquals(Filter.ExpressionType.OR, expression.type());
        assertEquals(Filter.ExpressionType.AND, ((Filter.Expression) expression.left()).type());
        assertEquals(Filter.ExpressionType.AND, ((Filter.Expression) expression.right()).type());
    }

    @Test
    void parseAndValidate_DirectAndBracketUrlParams_ParsesCorrectly() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("doc_id", "doc-042");
        params.add("page[gte]", "3");
        params.add("page[lte]", "10");

        MetadataFilter filter = service.parseAndValidate(params);

        assertNotNull(filter);
        assertEquals(LogicalOperator.AND, filter.rootOperator());
        assertEquals(3, filter.totalConditionCount());

        Filter.Expression expression = service.toFilterExpression(filter);
        assertNotNull(expression);
        assertEquals(Filter.ExpressionType.AND, expression.type());
    }

    @Test
    void parseAndValidate_InOperatorAndValueWithColons_ParsesCorrectly() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "org_unit:in:unit-a,unit-b");
        params.add("filter", "source_url:eq:https://example.org/docs/policy.pdf");

        MetadataFilter filter = service.parseAndValidate(params);

        assertNotNull(filter);
        List<FilterCondition> conditions = filter.groups().get(0).conditions();
        assertEquals(List.of("unit-a", "unit-b"), conditions.get(0).value());
        assertEquals("https://example.org/docs/policy.pdf", conditions.get(1).value());

        Filter.Expression expression = service.toFilterExpression(filter);
        assertNotNull(expression);
        assertEquals(Filter.ExpressionType.AND, expression.type());
    }

    @Test
    void parseAndValidate_NonWhitelistedField_ThrowsExceptionListingValidFields() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "password_hash:eq:secret");

        InvalidMetadataFilterException ex = assertThrows(
                InvalidMetadataFilterException.class,
                () -> service.parseAndValidate(params)
        );

        assertTrue(ex.getMessage().contains("password_hash"));
        assertTrue(ex.getValidFields().contains("doc_id"));
        assertTrue(ex.getValidFields().contains("source"));
        assertTrue(ex.getValidFields().contains("page"));
    }

    @Test
    void parseAndValidate_UnknownDirectQueryParam_ThrowsExceptionListingValidFields() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("query", "audit");
        params.add("sql_injection", "1 OR 1=1");

        InvalidMetadataFilterException ex = assertThrows(
                InvalidMetadataFilterException.class,
                () -> service.parseAndValidate(params)
        );

        assertTrue(ex.getMessage().contains("sql_injection"));
        assertTrue(ex.getValidFields().contains("doc_id"));
    }

    @Test
    void parseAndValidate_DisallowedOperator_ThrowsException() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "doc_id:like:doc-%");

        InvalidMetadataFilterException ex = assertThrows(
                InvalidMetadataFilterException.class,
                () -> service.parseAndValidate(params)
        );

        assertTrue(ex.getMessage().contains("like"));
        assertTrue(ex.getValidOperators().contains("eq"));
    }

    @Test
    void parseAndValidate_InvalidLogicalOperator_ThrowsException() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "doc_id:eq:doc-1");
        params.add("logic", "XOR");

        InvalidMetadataFilterException ex = assertThrows(
                InvalidMetadataFilterException.class,
                () -> service.parseAndValidate(params)
        );

        assertTrue(ex.getMessage().contains("XOR"));
    }

    @Test
    void parseAndValidate_MalformedFilterExpression_ThrowsException() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "doc_id:eq");

        assertThrows(InvalidMetadataFilterException.class, () -> service.parseAndValidate(params));
    }

    @Test
    void parseAndValidate_InvalidIntegerType_ThrowsException() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("filter", "page:gte:not-a-number");

        InvalidMetadataFilterException ex = assertThrows(
                InvalidMetadataFilterException.class,
                () -> service.parseAndValidate(params)
        );

        assertTrue(ex.getMessage().contains("integer"));
    }

    @Test
    void parseAndValidate_InvalidBooleanTypeAndRangeOperatorOnBoolean_ThrowsException() {
        MultiValueMap<String, String> invalidBool = new LinkedMultiValueMap<>();
        invalidBool.add("filter", "is_latest:eq:maybe");
        assertThrows(InvalidMetadataFilterException.class, () -> service.parseAndValidate(invalidBool));

        MultiValueMap<String, String> rangeOnBool = new LinkedMultiValueMap<>();
        rangeOnBool.add("filter", "is_latest:gt:true");
        assertThrows(InvalidMetadataFilterException.class, () -> service.parseAndValidate(rangeOnBool));
    }

    @Test
    void parseAndValidate_ControlCharactersOrTooLongValue_ThrowsException() {
        MultiValueMap<String, String> controlChar = new LinkedMultiValueMap<>();
        controlChar.add("filter", "doc_id:eq:doc-1\u0000injected");
        assertThrows(InvalidMetadataFilterException.class, () -> service.parseAndValidate(controlChar));

        MultiValueMap<String, String> tooLong = new LinkedMultiValueMap<>();
        tooLong.add("filter", "doc_id:eq:" + "a".repeat(101));
        assertThrows(InvalidMetadataFilterException.class, () -> service.parseAndValidate(tooLong));
    }

    @Test
    void parseAndValidate_ExceedingMaxConditions_ThrowsException() {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        for (int i = 1; i <= 6; i++) {
            params.add("filter", "doc_id:eq:doc-" + i);
        }

        InvalidMetadataFilterException ex = assertThrows(
                InvalidMetadataFilterException.class,
                () -> service.parseAndValidate(params)
        );

        assertTrue(ex.getMessage().contains("Maximum allowed is 5"));
    }
}
