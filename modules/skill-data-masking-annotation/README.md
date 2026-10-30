# skill-data-masking-annotation

DTO/VO 클래스 및 Java 21 Record 필드에 `@Mask(MaskType.PHONE/NAME/EMAIL/CARD)` 어노테이션을 부여하면 리플렉션을 통해 로깅 및 전송용 마스킹 Map/문자열을 자동 생성하는 단일 기술 모듈입니다.

## 주요 기능
- `@Mask(type = MaskType.PHONE)`: 필드 단위 마스킹 선언
- `AnnotationDataMasker.toMaskedMap(dto)`: 마스킹 적용된 `Map<String, Object>` 반환
- `AnnotationDataMasker.toMaskedString(dto)`: 안전한 로깅용 `Dto{name=홍*동, ...}` 문자열 생성
