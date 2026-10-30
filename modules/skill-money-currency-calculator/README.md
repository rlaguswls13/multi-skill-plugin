# skill-money-currency-calculator

부동소수점 오차 없는 통화(KRW, USD, EUR, JPY 등) 금액 불변 객체, 통화별 소수점 자릿수 자동 절삭 및 은행가 반올림(HALF_EVEN), 통화 기호 포맷팅, 환율 환산 기능을 제공하는 단일 기술 모듈입니다.

## 주요 기능
- `Money.of(15000, Currency.KRW)`: 통화 객체 생성 (소수점 0자리 고정)
- `Money.of(10.556, Currency.USD)`: 소수점 2자리 반올림 (`$10.56`)
- `m1.plus(m2)`, `m1.minus(m2)`, `m1.multiply(factor)`: 금융 금액 연산
- `m.format()`: 통화 기호 부착 콤마 포맷 (`₩18,500`, `$10.56`)
- `m.convert(Currency.KRW, exchangeRate)`: 환율 곱산 통화 변환
