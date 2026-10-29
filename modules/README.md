# 📦 `modules/` — 단일 기술 모듈 디렉터리

이 디렉터리 하위의 각 폴더(`modules/<skill-name>`)는 **단일 기술(Single Skill)**을 제공하는 독립 Gradle 서브모듈이며, 빌드 시 다른 프로그램에서 단독으로 임포트하여 사용할 수 있는 **개별 `.jar` 파일**을 생성합니다.

## 신규 모듈 생성 방법

1. 외부 위키(`D:\obsidian-storage\project-rag\multi-skill-plugin\02-modules\module-catalog.md`)에 모듈 스펙을 먼저 정의합니다.
2. Git Flow 규칙에 따라 `feature/<skill-name>` 브랜치를 생성합니다 (세부 수정 사항은 `sub/<skill-name>/<task>`로 분기).
3. `modules/<skill-name>/build.gradle` 파일을 생성하면 루트 `settings.gradle`이 자동으로 `:modules:<skill-name>` 모듈로 등록합니다:
   ```groovy
   description = '단일 기술 모듈 설명'

   dependencies {
       // 외부 의존성을 최소화하고 순수 Java 21 API 우선 사용
   }
   ```
4. 개별 JAR 빌드 및 테스트:
   ```powershell
   .\gradlew.bat :modules:<skill-name>:build
   ```
   - 산출물: `modules/<skill-name>/build/libs/<skill-name>-1.0.0.jar`
