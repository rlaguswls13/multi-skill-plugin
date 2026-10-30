# skill-user-agent-parser

외부 무거운 의존성 없이 순수 Java 21 정규식 매칭 기반으로 브라우저, 브라우저 버전, OS, 디바이스 유형(DESKTOP/MOBILE/TABLET/BOT), 및 주요 봇(Googlebot, Bingbot, Slackbot 등)을 고속 판별하는 단일 기술 모듈입니다.

## 주요 기능
- `UserAgentParser.parse(uaString)`: User-Agent 문자열 파싱
- `DeviceType`: `DESKTOP`, `MOBILE`, `TABLET`, `BOT`, `UNKNOWN`
- `ua.isBot()`: 크롤러/봇 여부 및 봇 명칭 추출
