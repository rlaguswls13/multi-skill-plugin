# skill-dynamic-expression-evaluator

무거운 SpEL/OGNL 라이브러리 없이 RPN(역폴란드 표기법) 알고리즘 기반으로 산술, 비교(`==`, `!=`, `<`, `>`, `<=`, `>=`), 논리(`&&`, `||`), 괄호, 문자열 및 컨텍스트 변수 치환을 안전하게 수행하는 순수 Java 21 경량 동적 표현식 평가기입니다.

## 주요 기능
- `ExpressionEvaluator.evaluate("2 + 3 * 4", ctx)`: 산술 연산자 우선순위 평가
- `ExpressionEvaluator.evaluateCondition("amount >= 50000 && grade == 'VIP'", ctx)`: 조건식 Boolean 평가
- 단일/더블 인용부호 문자열 리터럴, Boolean, Double, Long 자동 타입 처리
