# 🧩 [multi-skill-plugin 에이전트 통합 지침 및 아키텍처 스키마] (`AGENTS.md`)

본 문서는 AI 코딩 에이전트(Antigravity, Claude Code, Cursor 등)가 `multi-skill-plugin` (단일 기술 모듈 및 개별 `.jar` 기능 모음집) 프로젝트를 설계·개발·유지보수할 때 최우선으로 준수해야 하는 **최상위 진입점(Master Entry Point)**입니다.

> ⚠️ **중앙 RAG 볼트(`project-rag`) 연동 안내**
> 이 프로젝트의 위키(지식 베이스, 기술 설계, Git Flow, 애자일 백로그/칸반/스크럼 및 세션 핸드오프) 정본(SSOT)은 로컬 리포지토리가 아닌 중앙 RAG 볼트에 있습니다:
> - **중앙 볼트 경로 (SSOT)**: `D:\obsidian-storage\project-rag\multi-skill-plugin\` (환경변수 `PROJECT_RAG_PATH`가 있으면 `$PROJECT_RAG_PATH\multi-skill-plugin`)
> - 로컬 리포지토리의 `wiki/`는 중앙 볼트를 가리키는 디렉터리 정션(Git 제외)이며, `.wiki/README.md`와 `.agent/session-handoff.md`는 안내 스텁입니다.

---

## 1. 🏛️ 정본 지식 베이스 백링크 (SSOT Knowledge Links)

- **[Level 0] 위키 홈 & 마스터 인덱스**:
  - [`README.md`](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/README.md) / [`index.md`](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/index.md)
  - [`Session_Handoff.md` (최신 세션 작업 상태 SSOT)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/Session_Handoff.md)
- **[Level 1] 모듈러 아키텍처 & 기술 카탈로그**:
  - [단일 기술 모듈 및 개별 JAR 아키텍처 (`01-architecture/modular-jar-architecture.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/01-architecture/modular-jar-architecture.md)
  - [단일 기술 모듈 카탈로그 (`02-modules/module-catalog.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/02-modules/module-catalog.md)
- **[Level 2] Git Flow 브랜치 전략**:
  - [3계층 Git Flow 전략 (`04-git-flow/git-flow-strategy.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/04-git-flow/git-flow-strategy.md)
- **[Level 3] 애자일 (칸반 / 스크럼) 일정 관리 & 보고서**:
  - [애자일 운영 규격 (`05-agile-scrum/agile-governance.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/05-agile-scrum/agile-governance.md)
  - [프로덕트 백로그 (`05-agile-scrum/product-backlog.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/05-agile-scrum/product-backlog.md)
  - [실시간 칸반 보드 (`05-agile-scrum/kanban-board.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/05-agile-scrum/kanban-board.md)
  - [스크럼 일지 (`05-agile-scrum/sprint-scrum-log.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/05-agile-scrum/sprint-scrum-log.md)
  - [Sprint #1 애자일 보고서 (`06-reports/sprint-01-report.md`)](file:///D:/obsidian-storage/project-rag/multi-skill-plugin/06-reports/sprint-01-report.md)

---

## 2. 🧠 Wiki-First Thinking (위키 기반 코드 및 기술 사고 원칙)

1. **선(先) 위키 탐색 및 설계, 후(後) 코딩**:
   - 모든 기술 구현이나 코드 수정 전 반드시 `D:\obsidian-storage\project-rag\multi-skill-plugin\` 내의 아키텍처 문서와 모듈 카탈로그를 먼저 읽고 설계(Think)합니다.
   - 새로운 기술이나 설계 변경이 발생하면 위키의 모듈 카탈로그·아키텍처 문서를 먼저 갱신한 뒤 코드를 작성합니다.
2. **단일 기술 = 단일 모듈 = 개별 JAR 원칙 (`modules/<skill-name>`)**:
   - 본 프로젝트는 간단한 단일 기술을 독립 모듈 또는 개별 `.jar` 형태로 제공하여, 다른 프로그램에서 복잡한 설정 없이 즉시 가져다 쓸 수 있는 기능 모음집을 목표로 합니다.
   - 모듈 간 결합도를 0에 가깝게 유지하며, 무거운 프레임워크 의존성 없이 순수 Java 표준 라이브러리 기반으로 우선 구현합니다.

---

## 3. 🌿 Git Flow 브랜치 전략 (`main` ← `feature/*` ← `sub/*`)

1. **기능별 브랜치 (`feature/<기능명>`)**:
   - 신규 단일 기술 모듈이나 기능 단위 작업 시 `main`에서 `feature/<기능명>` 브랜치를 생성합니다.
2. **세부 수정 서브 브랜치 (`sub/<기능명>/<세부작업>`)**:
   - 각 기능 안에서 세부 구현, 수정 사항, 버그 패치, 리팩토링이 필요할 때는 반드시 `sub/<기능명>/<세부작업>` 서브 브랜치를 구분하여 작업한 뒤 `feature/<기능명>`로 병합합니다.
3. **최종 배포 브랜치 (`main`)**:
   - 단위 테스트 및 개별 `.jar` 빌드 검증이 완료된 최종본만 `main`에 병합(`--no-ff`)하고 Public GitHub(`https://github.com/rlaguswls13/multi-skill-plugin`)에 배포합니다.

---

## 4. 📋 애자일 (백로그 / TODO / 칸반 / 스크럼) 관리 수칙

- 모든 기능 개발과 수정 작업은 `05-agile-scrum/product-backlog.md`(백로그) → `kanban-board.md`(`Backlog` / `TODO` / `In Progress` / `Review & QA` / `Done`) → `sprint-scrum-log.md`(스크럼 일지) 흐름에 맞춰 상태를 갱신합니다.
- 프로젝트 보고서는 항상 **칸반 보드 현황 + 스크럼 요약(Done / TODO / Blockers) + Git Flow 브랜치 상태 + 개별 JAR 산출물**을 포함하여 작성합니다.
