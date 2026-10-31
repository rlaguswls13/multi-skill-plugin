# CSV & Text Encoding Sniffer (`skill-csv-encoding-sniffer`)

외부 무거운 라이브러리(juniversalchardet 등) 없이 Java 21 표준 라이브러리만으로 파일 또는 스트림의 바이트 패턴을 분석하여 문자 인코딩(Charset) 및 BOM(Byte Order Mark) 존재 여부를 자동 판별하는 스니퍼 모듈입니다.

국내 레거시 시스템, 공공데이터 포털, Excel 등에서 자주 발생하는 EUC-KR / CP949(MS949)와 UTF-8(BOM 유/무), UTF-16, US-ASCII 문서를 정확하게 구분합니다.

## 주요 기능

- **BOM(Byte Order Mark) 시그니처 판별**:
  - UTF-8 BOM (`EF BB BF`)
  - UTF-16LE (`FF FE`), UTF-16BE (`FE FF`)
  - UTF-32LE (`FF FE 00 00`), UTF-32BE (`00 00 FE FF`)
- **휴리스틱 바이트 시퀀스 검증**:
  - 순수 7-bit US-ASCII 빠른 판별
  - UTF-8 멀티바이트 시퀀스 유효성 검증
  - 한국어 2바이트 완성형(EUC-KR: `0xA1..0xFE`) 및 확장 완성형(CP949/MS949: `0x81..0xFE`) 판별
  - 실패 시 ISO-8859-1 (Latin-1) Fallback
- **스트림 기반 샘플링 지원**:
  - `InputStream`에서 선두 N바이트(기본 8KB)만 읽어 스트림 전체를 메모리에 올리지 않고 인코딩 판별
- **신뢰도 지표 (`EncodingConfidence`)**:
  - Charset, confidence (0.0 ~ 1.0), hasBom 플래그 제공

## 사용 예제

```java
import com.msp.encodingsniffer.CsvEncodingSniffer;
import com.msp.encodingsniffer.EncodingConfidence;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

// 1. 바이트 배열 직접 판별
byte[] sampleBytes = ...; // CSV 파일에서 읽은 앞부분 바이트
EncodingConfidence result = CsvEncodingSniffer.detect(sampleBytes);

System.out.println("Charset: " + result.charset());       // e.g. UTF-8 or MS949
System.out.println("Confidence: " + result.confidence()); // e.g. 0.99
System.out.println("Has BOM: " + result.hasBom());       // true / false

// 2. InputStream에서 첫 8KB 샘플링하여 인코딩 추천받기
try (InputStream is = new FileInputStream("customer_data.csv")) {
    Charset charset = CsvEncodingSniffer.detectCharset(is);
    System.out.println("Recommended Charset: " + charset.name());
}
```
