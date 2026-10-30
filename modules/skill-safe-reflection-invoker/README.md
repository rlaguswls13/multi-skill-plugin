# Fluent Safe Reflection Invoker (`skill-safe-reflection-invoker`)

번거로운 Java 체크드 예외(Checked Exceptions) 없이 private 필드 접근, 메서드 호출, 동적 인스턴스 생성을 플루언트(Fluent)하게 수행하고 내부 캐싱을 지원하는 리플렉션 유틸리티입니다.

## Features
- **Unchecked Safe Exception**: `NoSuchFieldException`, `IllegalAccessException` 등을 `SafeReflectionException` 런타임 예외로 래핑하여 보일러플레이트 코드 제거.
- **Hierarchy Search**: 상속 계층 구조를 끝까지 거슬러 올라가 상위 클래스의 private 필드 및 메서드까지 자동 탐색.
- **Member Caching**: `ConcurrentHashMap` 기반 리플렉션 메타데이터 캐싱으로 반복 호출 오버헤드 최소화.
- **Zero Dependencies**: 순수 Java 21 표준 라이브러리(`java.lang.reflect`).

## Usage

```java
// 1. private 필드 읽기 및 변경
String name = SafeReflection.of(user).getFieldValue("secretApiKey");
SafeReflection.of(user).setFieldValue("active", true);

// 2. private 메서드 호출
String result = SafeReflection.of(service).invokeMethod("calculateInternal", 10, "USD");

// 3. 동적 객체 생성
SampleBean bean = SafeReflection.newInstance(SampleBean.class, "param1", 100);
```
