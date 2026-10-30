# skill-sensitive-data-anonymizer

개인정보보호법 및 GDPR 컴플라이언스를 위한 데이터 비식별화(K-익명성 연령/주소 범주화, HMAC 가명화, 차분 프라이버시 국소 섭동 노이즈) 단일 기술 모듈입니다.

## 주요 기능
- `Anonymizer.generalizeAge(27)`: 나이 10년 단위 범주화 (`"20대"`)
- `Anonymizer.generalizeAddress(fullAddr)`: 시/도/구 단위 절삭 (`"서울특별시 강남구"`)
- `Anonymizer.pseudonymize(val, salt)`: HMAC-SHA256 결정론적 가명화 해시
- `Anonymizer.perturb(val, noise)`: 차분 프라이버시 국소 잡음 추가
- `Anonymizer.mask(text, start, end, char)`: 부분 마스킹
