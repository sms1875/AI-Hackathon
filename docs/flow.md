# 프로젝트 진행 흐름

## 2026-03-13

### 1. GitHub 레포지토리 생성 및 초기 커밋
**요청:** `D:\work\AI해커톤` 디렉토리를 GitHub에 올려달라

**수행 작업:**
- `git init` 실행
- `gh repo create sms1875/AI-Hackathon --public` 으로 GitHub 레포 생성
- `.claude/`, `.gitignore`, `CLAUDE.md`, `docs/`, `README.md` 스테이징 및 초기 커밋
- `git push -u origin master` 로 푸시 완료

**결과:** https://github.com/sms1875/AI-Hackathon

---

### 2. 프로젝트 분석
**요청:** CLAUDE.md, README.md 등을 분석해서 이 프로젝트가 어떤 프로젝트인지 설명해달라

**수행 작업:**
- `README.md`, `docs/PRD.md`, `.claude/agents/*.md`, `.claude/skills/*.md` 파일 읽기 및 분석

**결과:**
- 프로젝트 정체: Claude Code 에이전트/스킬 설정을 공유하는 템플릿 레포 (choiji-guide)
- 핵심 목적: PRD → ROADMAP → 스프린트 계획 → 구현 → 마무리 전 사이클 반자동화
- 구성: 4개 에이전트 (prd-to-roadmap, sprint-planner, sprint-close, code-reviewer) + 2개 스킬 (writing-plans, karpathy-guidelines)

---

### 3. 다음 할 일 안내
**요청:** 처음에 무엇을 해야 하는지 물어봄

**수행 작업:**
- `docs/PRD.md` 파일 상태 확인 → 비어있음(1줄)
- 워크플로우 첫 단계가 PRD 작성임을 안내

**결과:** PRD.md 작성이 첫 번째 작업임을 확인. 만들 서비스 내용 대기 중

---

### 4. flow.md 생성
**요청:** 요청 내용과 수행 작업을 flow.md로 정리해달라

**수행 작업:**
- `docs/flow.md` 파일 생성 (현재 문서)

---

### 5. PRD 초안 작성 (WebFetch 기반)
**요청:** GitHub 레포(https://github.com/sms1875/GamePaper)를 분석해서 PRD 초안 작성해달라

**수행 작업:**
- Explore 에이전트로 GitHub WebFetch 분석 (README, pubspec.yaml, build.gradle, 디렉토리 구조 등)
- 기존 앱/서버 구조 파악 후 `docs/PRD.md` 초안 작성

**결과:** `docs/PRD.md` 초안 생성

---

### 6. 코드 직접 분석 후 PRD 보완
**요청:** 깃 클론 받아서 실제 코드 기반으로 분석 후 PRD 작성에 필요한 내용 정리

**수행 작업:**
- `/tmp/GamePaper` (기존 클론) 활용하여 Explore 에이전트로 전체 소스 분석
  - Flutter: models, providers, repositories, screens, services, widgets 전체
  - Spring Boot: controllers, services (크롤러 6개), firebase 서비스, domain
  - application.properties, pubspec.yaml, build.gradle 실제 의존성 확인
- 발견 내용: 6개 게임 지원, 기술 부채 7개 항목, AI 연동 포인트 6개 도출
- `docs/PRD.md` 코드 기반으로 전면 재작성

**결과:** `docs/PRD.md` 업데이트 — AS-IS/TO-BE 구조, 기술 부채 목록, AI 기능 연동 포인트 상세화

---

### 7. PRD 배포 전략 반영
**요청:** GitHub Actions CI/CD, 로컬→클라우드 단계별 전환 가능한 구조를 PRD에 반영해달라

**수행 작업:**
- 기술 스택 섹션에 Phase 1(로컬)/Phase 2(클라우드) 단계별 전환 전략 추가
- `StorageService`, `DatabaseRepository` 인터페이스 추상화 설계 추가
- Spring Profile(`local`/`prod`) 기반 구현체 분기 방식 명시
- 배포 전략 섹션(Phase 1: 로컬 Docker Compose, Phase 2: GitHub Actions → 클라우드) 추가
- 미결 사항에서 Firebase 고정 표현 → 클라우드 미정으로 수정

**결과:** `docs/PRD.md` 배포 전략 및 추상화 설계 반영 완료

---

### 8. PRD AI 범용 크롤러 및 추가 기능 반영
**요청:** 게임 URL만 DB에 넣으면 AI가 파싱 전략을 자동 분석·생성하는 구조 반영, PRD 추가 내용 보완

**수행 작업:**
- 프로젝트 목표에 AI 범용 크롤러 핵심 비전 추가
- `[AI 핵심기능] AI 범용 크롤러` 섹션 신규 추가 (동작 흐름, 파싱 전략 스키마, 기존 크롤러 교체 계획)
- `[기능] 게임 관리 Admin UI` 섹션 추가 (게임 등록, 상태 조회, 재분석 트리거)
- 배경화면 설명 자동 생성에 Claude Vision 활용 명시
- Admin API 엔드포인트 섹션 추가
- 미결 사항에 AI 크롤러 관련 항목 추가

**결과:** `docs/PRD.md` AI 범용 크롤러 중심으로 전면 보완 완료

---

### 9. 프로젝트 우선순위 및 멀티플랫폼 확장 반영
**요청:** 우선순위를 1.AI 파싱 자동화 2.배포/저장소 추상화 3.리팩토링 4.멀티플랫폼·신규기능 으로 정의하고 PRD에 반영

**수행 작업:**
- 목표 섹션에 4개 우선순위 및 실제 실행 순서(Phase 1~4) 명시
- Phase 4 멀티플랫폼 확장 섹션 신규 추가:
  - Flutter 데스크탑 지원 (Windows/macOS/Linux 배경화면 API 차이 명시)
  - 해상도별 이미지 추천 (메타데이터 저장 + 기기 해상도 감지)
  - PC 배경화면 이미지 수집 (가로형 이미지 크롤링 전략 확장)
  - 라이브 배경화면 (복잡도 높음 → 슬라이드쇼 우선 접근 제안)
- 비기능 요구사항에 플랫폼 지원, 환경 이식성 항목 추가
- 미결 사항을 AI크롤러/인프라/멀티플랫폼 3개 카테고리로 분류

**결과:** `docs/PRD.md` 4단계 우선순위 및 멀티플랫폼 확장 계획 반영 완료

---

### 10. Admin 관리자 페이지 상세화
**요청:** 서버 관리자 페이지에서 게임 추가, 배경화면 종류/현황 확인 기능 추가

**수행 작업:**
- 기존 단순 Admin UI → 4개 페이지 구조로 상세화
  - 대시보드 / 게임 목록 / 게임 등록 / 게임 상세
- 게임 상세 페이지에 배경화면 갤러리, 해상도별·태그별 분류, 파싱전략 에디터, 크롤링 로그 탭 추가
- AI 분석 진행 상태 실시간 표시 (SSE/polling) 명시
- Admin API 엔드포인트 상세 추가 (11개)

**결과:** `docs/PRD.md` Admin Dashboard 4페이지 구조 및 API 상세 반영 완료

---

### 11. PRD 전면 재작성 (정리)
**요청:** 여러 번 추가된 내용들을 하나의 깔끔한 문서로 정리해달라

**수행 작업:**
- PRD 전체 구조 재설계: 개요 → AS-IS → 타겟 → 기술스택 → 기능(Phase별) → 배포 → 비기능 → API → 미결사항
- Phase 1~4로 기능 요구사항 정렬 (기초/AI크롤러+관리자/리팩토링/멀티플랫폼)
- 중복·흩어진 내용 통합, Firebase 고정 표현 제거

**결과:** `docs/PRD.md` 전면 재작성 완료

---

### 12. ROADMAP.md 생성
**요청:** prd-to-roadmap 에이전트 실행

**수행 작업:**
- `prd-to-roadmap` 에이전트가 `docs/PRD.md` 분석 후 `docs/ROADMAP.md` 생성

**결과:** `docs/ROADMAP.md` 생성 완료 — 4 Phase, 8 Sprint, 16주 계획
- Phase 1 (Sprint 1-2): 인프라 기초 (DB/스토리지 추상화, Docker)
- Phase 2 (Sprint 3-5): AI 범용 크롤러 + 관리자 UI
- Phase 3 (Sprint 6): 리팩토링
- Phase 4 (Sprint 7-8): 멀티플랫폼 확장

---

### 13. 커밋 및 푸시
**요청:** 현재 상태 검토 후 이상 없으면 커밋 및 푸시

**수행 작업:**
- 전체 파일 검토 (PRD.md, ROADMAP.md, CLAUDE.md, flow.md)
- `.claude/agent-memory/` 포함 여부 확인 → 포함으로 결정
- `git add .` → 커밋 → `git push origin master`

**결과:** `a0a57d7` 커밋 푸시 완료 — https://github.com/sms1875/AI-Hackathon

---

### 14. flow.md 정리 및 README 갱신 규칙 추가
**요청:** 완료된 작업은 flow.md에서 정리, 커밋 시 README 항상 갱신 규칙 추가

**수행 작업:**
- `docs/flow.md` 다음 예정 작업 섹션 완료 항목 제거
- `README.md` 프로젝트 실제 내용으로 전면 갱신
- `CLAUDE.md`에 README 갱신 규칙 추가
- 영구 메모리에 두 규칙 저장

**결과:** flow.md 정리, README.md 갱신, 규칙 영속화 완료

---

### 15. ROADMAP 기간 표기 제거
**요청:** 전체 예상 기간(약 16주) 등 기간 명시 제거, 스프린트 단위로만 표기

**수행 작업:**
- `docs/ROADMAP.md` 수정:
  - `전체 예상 기간: 8 스프린트 (약 16주)` → `전체 스프린트: 8 스프린트`
  - Sprint 1~8 헤더에서 `(2주)` 제거
  - 마일스톤 테이블 `(N주차)` 제거, `예상 시점` → `목표 스프린트`
  - `기준일` 항목 제거

**결과:** `docs/ROADMAP.md` 기간 표기 전면 제거 완료

---

### 16. 기술사양·데이터흐름·검토 문서 추가
**요청:** PRD, ROADMAP 검토, 기술사양, 데이터흐름 등 추가 문서 작성

**수행 작업:**
- `docs/TECH-SPEC.md` 생성: 패키지 구조, DB 스키마, StorageService 인터페이스, 파싱 전략 JSON 스키마, API 스펙, 환경변수, Docker Compose, 성능/보안 고려사항
- `docs/DATA-FLOW.md` 생성: 게임 등록/AI 분석 흐름, 크롤링 파이프라인, AI 태그 생성, Flutter 앱 조회, AI 추천, 이미지 서빙, 데이터 생명주기, 오류 처리 흐름
- `docs/REVIEW.md` 생성: PRD↔ROADMAP 정합성 검토, 미결 사항 권고, 리스크 분석, Sprint 의존성, DoD 체크리스트, Phase 1 진입 전 체크리스트
- `README.md` 문서 목록 업데이트

**결과:** 핵심 문서 5종 완비 (PRD, ROADMAP, TECH-SPEC, DATA-FLOW, REVIEW)

---

### 17. 브랜치 전략 정의 및 적용
**요청:** 브랜치 전략 정의 및 적용

**수행 작업:**
- Option A (단순 전략) 선택: `master` / `sprint{n}` / `hotfix/{name}`
- `docs/BRANCH-STRATEGY.md` 생성: 브랜치 구조, 워크플로우, 커밋 메시지 규칙, PR 규칙
- `CLAUDE.md` 브랜치 전략 규칙 업데이트 (BRANCH-STRATEGY.md 참조 추가)
- `README.md` 문서 목록에 BRANCH-STRATEGY 추가
- GitHub `master` 브랜치 보호 규칙 적용 (force push 금지, 삭제 금지)

**결과:** 브랜치 전략 문서화 및 GitHub 보호 규칙 적용 완료

---

### 18. PRD·ROADMAP 검토 및 수정
**요청:** 스프린트 시작 전 PRD, ROADMAP, karpathy-guidelines 반영 여부 검토 및 수정

**수행 작업:**
- PRD 수정 (5개 이슈):
  - 섹션 9 미결 사항 → 결정된 내용 반영 (HTML+스크린샷, 자동+수동 재분석, Provider 유지)
  - CI/CD 브랜치 `main` → `master` 수정
  - Admin UI 인증 Won't Have 범위 명시
  - 라이브 배경화면 Could Have → Won't Have 통일
  - API 경로 `{game}` → `{gameId}` 수정
- ROADMAP 수정 (6개 이슈):
  - 대시보드 `4 (8 스프린트)` → `4`
  - S1-2 `listFiles()` → Sprint 3으로 이동 (Simplicity First)
  - Sprint 1 DoD에 WAL 모드 확인 항목 추가
  - S2-1 CrawlerScheduler 분리 및 Sprint 4 재활용 관계 명시
  - Sprint 2 검증 시나리오 — 존재하지 않는 크롤링 트리거 API 제거
  - S5-3 UserLike 엔티티 생성 태스크 추가

**결과:** PRD·ROADMAP 정합성 확보, Karpathy 가이드라인 반영 완료

---

## 다음 예정 작업
- ⬜ `sprint-planner` 에이전트로 `docs/sprint/sprint1.md` 생성
- ⬜ Sprint 1 구현 시작
