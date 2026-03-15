# GamePaper AI

게임 공식 사이트의 배경화면을 AI로 자동 수집하여 모바일/데스크탑 기기에 적용하는 앱입니다.

## 프로젝트 개요

기존 [GamePaper](https://github.com/sms1875/GamePaper) 앱을 리팩토링하고 AI 기능을 추가합니다.

- **AI 범용 크롤러**: 게임 URL만 등록하면 Claude AI가 페이지를 분석하여 파싱 전략을 자동 생성
- **환경 독립 아키텍처**: 로컬 PC에서 시작하여 클라우드로 전환 가능한 추상화 설계
- **Admin Dashboard**: 웹 UI에서 게임 등록, 배경화면 현황, 크롤링 상태 관리
- **멀티플랫폼**: Flutter 기반 Android → 데스크탑(Windows/macOS/Linux) 확장 예정

## 기술 스택

| 구분 | 기술 |
|------|------|
| 모바일/데스크탑 앱 | Flutter (Dart) |
| 백엔드 | Spring Boot 3.x (Java 21) |
| 크롤링 | Selenium + Jsoup |
| AI | Claude API (Anthropic) |
| DB (Phase 1) | SQLite |
| 스토리지 (Phase 1) | 로컬 파일 시스템 |
| 배포 | Docker Compose + GitHub Actions |

## 개발 로드맵

| Phase | 내용 | 상태 |
|-------|------|------|
| Phase 1 | 인프라 기초 (DB/스토리지 추상화, Docker) | ✅ 완료 (Sprint 1-2 완료) |
| Phase 2 | AI 범용 크롤러 + 관리자 페이지 | 🔄 진행 중 (Sprint 4 완료, Sprint 5 예정) |
| Phase 3 | 리팩토링 (캐시, 에러 처리, 테스트) | 📋 예정 |
| Phase 4 | 멀티플랫폼 확장 (데스크탑, 해상도별 추천) | 📋 예정 |

전체 로드맵: [docs/ROADMAP.md](docs/ROADMAP.md)

## 문서

| 문서 | 설명 |
|------|------|
| [PRD](docs/PRD.md) | 제품 요구사항 문서 (기능, AS-IS/TO-BE, API 설계) |
| [ROADMAP](docs/ROADMAP.md) | 4 Phase, 8 Sprint 개발 로드맵 |
| [TECH-SPEC](docs/TECH-SPEC.md) | 기술 사양서 (패키지 구조, DB 스키마, API 스펙, Docker 구성) |
| [DATA-FLOW](docs/DATA-FLOW.md) | 데이터 흐름 (크롤링 파이프라인, AI 분석, 이미지 서빙 등) |
| [REVIEW](docs/REVIEW.md) | PRD·ROADMAP 정합성 검토, 리스크, 미결 사항 |
| [BRANCH-STRATEGY](docs/BRANCH-STRATEGY.md) | 브랜치 전략 (master / sprint{n} / hotfix) |
| [deploy.md](docs/deploy.md) | 배포 및 검증 가이드 (자동/수동 항목 구분) |
| [flow.md](docs/flow.md) | 작업 이력 |

## 개발 환경 설정

### 서버 실행 (Docker)

```bash
cd server/

# 최초 실행 전 사전 준비
touch gamepaper.db
mkdir -p storage/images
cp .env.example .env

# 서버 빌드 및 실행
docker compose up --build
```

서버 기동 후 `http://localhost:8080/api/games` 로 동작 확인.
상세 가이드: [server/deploy.md](server/deploy.md) | 전체 배포/검증: [docs/deploy.md](docs/deploy.md)

### 앱 실행 (Flutter)

```bash
cd client
flutter run
```

## Claude Code 설정

이 레포지토리는 Claude Code 에이전트/스킬을 포함합니다.

| 에이전트/스킬 | 역할 |
|--------------|------|
| `prd-to-roadmap` | PRD → ROADMAP 자동 생성 |
| `sprint-planner` | 스프린트 계획 수립 |
| `sprint-close` | 스프린트 마무리 (PR, 코드 리뷰, 검증) |
| `code-reviewer` | 코드 리뷰 (Critical/Important/Suggestion) |
| `writing-plans` | 구현 전 단계별 계획 작성 |
| `karpathy-guidelines` | LLM 코딩 실수 방지 가이드라인 |
