# skill-semantic-git-tagger

Conventional Commits(`feat:`, `fix:`, `BREAKING CHANGE:`) 로그를 파싱하여 차기 SemVer 버전(Major/Minor/Patch) 자동 계산 및 Markdown 변경 로그(Changelog)를 생성하는 CI/CD 배포 자동화 단일 기술 모듈입니다.

## 주요 기능
- `GitTagCalculator.parseCommitMessage(hash, msg)`: Conventional Commit 파싱
- `GitTagCalculator.calculateNextVersion(current, commits)`: SemVer 버전 자동 산출
- `GitTagCalculator.generateChangelog(version, commits)`: Markdown Changelog 생성
