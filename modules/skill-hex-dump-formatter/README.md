# skill-hex-dump-formatter

네트워크 패킷이나 파일 바이너리 데이터를 분석하기 위해 오프셋 주소(8자리 Hex), 16진수 바이트 덤프, ASCII 인쇄 문자 뷰(`|...|`)를 일목요연하게 출력하는 Wireshark/xxd 스타일 Hex Dump 단일 기술 모듈입니다.

## 주요 기능
- `HexDumpFormatter.format(bytes)`: 기본 16바이트 표준 헥스 덤프 포맷
- `HexDumpOptions`: 행당 바이트 수, ASCII 출력 여부, 대소문자 커스텀 지원
