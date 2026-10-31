package com.msp.paramquery;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueryParameterBuilderTest {

    @Test
    @DisplayName("기본 키-값 직렬화 및 RFC 3986 인코딩 검증")
    void testBasicBuild() {
        QueryParameterBuilder builder = new QueryParameterBuilder();
        builder.add("name", "John Doe");
        builder.add("age", 30);
        builder.add("city", "Seoul");

        String query = builder.build();
        assertEquals("?name=John%20Doe&age=30&city=Seoul", query);

        String queryNoPrefix = builder.buildWithoutQuestionMark();
        assertEquals("name=John%20Doe&age=30&city=Seoul", queryNoPrefix);
    }

    @Test
    @DisplayName("한글 및 특수문자 인코딩 검증")
    void testSpecialCharactersAndUtf8() {
        QueryParameterBuilder builder = new QueryParameterBuilder();
        builder.add("q", "스프링 부트 & 자바");

        String query = builder.build();
        assertTrue(query.contains("%EC%8A%A4%ED%94%84%EB%A7%81%20%EB%B6%80%ED%8A%B8%20%26%20%EC%9E%90%EB%B0%94"));
    }

    @Test
    @DisplayName("다중값(List) 추가 및 직렬화 검증")
    void testMultiValues() {
        QueryParameterBuilder builder = new QueryParameterBuilder();
        builder.add("role", List.of("ADMIN", "MANAGER", "USER"));

        String query = builder.build();
        assertEquals("?role=ADMIN&role=MANAGER&role=USER", query);
    }

    @Test
    @DisplayName("set, remove, clear 조작 검증")
    void testMutations() {
        QueryParameterBuilder builder = new QueryParameterBuilder();
        builder.add("tag", List.of("v1", "v2"));
        builder.add("status", "draft");

        // set replaces all values
        builder.set("tag", "v3");
        assertEquals("?tag=v3&status=draft", builder.build());

        // remove
        builder.remove("status");
        assertEquals("?tag=v3", builder.build());

        // clear
        builder.clear();
        assertEquals("", builder.build());
    }

    @Test
    @DisplayName("addAll Map 지원 검증")
    void testAddAll() {
        QueryParameterBuilder builder = new QueryParameterBuilder();
        Map<String, Object> map = Map.of(
                "page", 1,
                "categories", List.of("electronics", "books")
        );
        builder.addAll(map);

        String result = builder.build();
        assertTrue(result.contains("page=1"));
        assertTrue(result.contains("categories=electronics"));
        assertTrue(result.contains("categories=books"));
    }

    @Test
    @DisplayName("QueryParameterBuilder.parse: 다양한 쿼리스트링 및 전체 URL 역직렬화 검증")
    void testParse() {
        // 1. Full URL with query and fragment
        String url = "https://api.example.com/v1/search?query=hello%20world&role=admin&role=editor&flag#section-1";
        QueryParameterMap map = QueryParameterBuilder.parse(url);

        assertEquals("hello world", map.get("query"));
        assertEquals(List.of("admin", "editor"), map.getAll("role"));
        assertEquals("", map.get("flag"));
        assertTrue(map.containsKey("flag"));
        assertEquals(3, map.size());
        assertFalse(map.isEmpty());

        // 2. Query string without leading '?'
        QueryParameterMap map2 = QueryParameterBuilder.parse("a=100&b=200");
        assertEquals("100", map2.get("a"));
        assertEquals("200", map2.get("b"));

        // 3. Empty or null
        assertTrue(QueryParameterBuilder.parse(null).isEmpty());
        assertTrue(QueryParameterBuilder.parse("").isEmpty());
    }

    @Test
    @DisplayName("QueryParameterBuilder.from: 기존 쿼리 파싱 후 수정 및 재빌드 검증")
    void testFromExistingQuery() {
        String original = "?page=1&size=20&sort=desc";
        QueryParameterBuilder builder = QueryParameterBuilder.from(original);

        builder.set("page", 2);
        builder.add("filter", "active");

        String updated = builder.build();
        assertEquals("?page=2&size=20&sort=desc&filter=active", updated);
    }

    @Test
    @DisplayName("QueryParameterMap 불변성 및 getOptional 검증")
    void testMapImmutability() {
        QueryParameterMap map = QueryParameterBuilder.parse("key=val1&key=val2");
        assertEquals("val1", map.get("key"));
        assertEquals(List.of("val1", "val2"), map.getAll("key"));
        assertTrue(map.getOptional("key").isPresent());
        assertTrue(map.getOptional("non_existent").isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> map.getAll("key").add("val3"));
        assertThrows(UnsupportedOperationException.class, () -> map.asMap().put("newKey", List.of("val")));
    }
}
