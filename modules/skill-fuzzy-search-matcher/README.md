# skill-fuzzy-search-matcher

외부 검색엔진(Elasticsearch 등) 없이 인메모리에서 Levenshtein 편집 거리 및 문자열 유사도 알고리즘을 기반으로 오타 교정, 유사 검색어 매칭, 점수(Score) 순위 랭킹을 산출하는 단일 기술 모듈입니다.

## 주요 기능
- `FuzzyMatcher.levenshteinDistance("apple", "apply")`: 편집 거리 계산
- `FuzzyMatcher.similarity("hello", "hella")`: 0.0 ~ 1.0 유사도 계산
- `FuzzyMatcher.search(query, items, extractor, minScore, limit)`: 유사 검색어 랭킹 도출
