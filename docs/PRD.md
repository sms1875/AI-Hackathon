# GamePaper PRD (제품 요구사항 문서)

> 작성일: 2026-03-13
> 기반: https://github.com/sms1875/GamePaper 코드 분석

---

## 1. 프로젝트 개요

### 서비스 소개
**GamePaper**는 게임 공식 사이트의 배경화면을 자동 수집하여 모바일/데스크탑 기기에 손쉽게 적용할 수 있는 앱입니다.
기존 앱/서버를 리팩토링하고, **AI 기반 범용 크롤러**와 **추천 기능**을 추가하여 확장성과 사용자 경험을 개선합니다.

### 개발 우선순위

| 순서 | 목표 | 설명 |
|------|------|------|
| 1 | **AI 범용 크롤러** | 게임 URL만 등록하면 AI가 파싱 전략 자동 생성 |
| 2 | **환경 독립 아키텍처** | 로컬 → 클라우드 전환 시 코드 변경 최소화 |
| 3 | **구조 리팩토링** | 기술 부채 해소, 테스트 가능한 구조 |
| 4 | **멀티플랫폼 확장** | Flutter 데스크탑 지원, 해상도별 추천 등 |

### 구현 순서 (Phase)

> 1번 AI 크롤러는 DB/스토리지가 먼저 준비되어야 동작 확인이 가능하므로, 인프라 기초 작업을 선행합니다.

```
Phase 1: 인프라 기초      DB·스토리지 추상화, Docker 환경 구성
            ↓
Phase 2: AI 범용 크롤러   파싱 전략 자동 생성, 관리자 페이지
            ↓
Phase 3: 리팩토링         전체 구조 정리, 클라이언트 개선
            ↓
Phase 4: 멀티플랫폼       데스크탑 앱, 해상도별 추천, 신규 기능
```

---

## 2. 현재 상태 (AS-IS)

### 기술 스택
| 구분 | 기술 |
|------|------|
| 모바일 앱 | Flutter (Dart), Provider |
| 백엔드 | Spring Boot 3.4 (Java 21), AWS EC2 |
| 크롤링 | Selenium 4.23 + Jsoup 1.15 |
| 스토리지 | Firebase Storage, Firebase Auth, Firebase App Check |
| 이미지처리 | BlurHash, WebP |

### 지원 게임 (6개)
| 게임 | 크롤러 방식 | 대상 URL |
|------|-----------|---------|
| 원신 (Genshin Impact) | Selenium | hoyolab.com |
| 마비노기 | Selenium | mabinogi.nexon.com |
| 메이플스토리 모바일 | Selenium | m.maplestory.nexon.com |
| NIKKE | Selenium | nikke-kr.com |
| 파이널판타지 XIV | Jsoup | na.finalfantasyxiv.com |
| 검은사막 | Jsoup | kr.playblackdesert.com |

### 현재 기능
- 게임 목록 화면: 알파벳 정렬, 캐러셀/리스트 뷰 전환
- 배경화면 그리드: 페이징 (12개/페이지), BlurHash 미리보기
- 배경화면 적용: 홈화면 / 잠금화면 / 둘 다
- 백엔드 자동화: 6시간마다 크롤링 → Firebase Storage 업로드

### 주요 기술 부채
| 구분 | 문제 | 심각도 |
|------|------|--------|
| 서버 | 게임별 크롤러가 독립 클래스 → 신규 게임 추가 시 전체 클래스 작성 필요 | 높음 |
| 서버 | signed URL 15분 만료 → 클라이언트에서 이미지 접근 불가 | 높음 |
| 서버 | 게임 메타데이터 메모리에만 저장 → 재시작 시 소실 | 높음 |
| 클라이언트 | 메모리 캐시만 사용 → 앱 재시작 시 데이터 소실 | 높음 |
| 클라이언트 | Firebase Storage 전체 리스트 한 번에 로드 → 성능 저하 | 중간 |
| 클라이언트 | 에러 처리 문자열 매칭 기반 → 구조화 필요 | 중간 |

---

## 3. 타겟 사용자

- 게임 팬 / 게이머
- 모바일·PC 기기 커스터마이징에 관심 있는 사용자
- 게임 공식 아트를 배경화면으로 쓰고 싶지만 직접 찾기 번거로운 사용자

---

## 4. 기술 스택 (TO-BE)

### 단계별 전환 전략

초기에는 로컬 PC에서 실행 가능한 구조로 시작하고, 이후 클라우드 전환 시 코드 변경이 최소화되도록 추상화 레이어를 설계합니다.

| 구분 | Phase 1 (로컬) | Phase 2+ (클라우드, 미정) |
|------|--------------|------------------------|
| 백엔드 | Spring Boot 3.x (Java 21), 로컬 실행 | AWS EC2 / Railway / Render 등 |
| DB | SQLite (로컬 파일) | PostgreSQL / Firestore 등 |
| 이미지 스토리지 | 로컬 파일 시스템 (`/storage/`) | AWS S3 / Firebase Storage / R2 등 |
| 모바일 앱 | Flutter (Dart), Provider (유지 또는 Riverpod 검토) | 동일 |
| 크롤링 | Selenium + Jsoup | 동일 |
| AI | Claude API (Anthropic) | 동일 |
| 배포 | 로컬 실행 | GitHub Actions → 클라우드 자동 배포 |

### 추상화 설계 원칙

```
StorageService (interface)
  ├── LocalStorageService     → 로컬 파일 시스템 (Phase 1)
  └── CloudStorageService     → S3 / Firebase Storage 등 (Phase 2+)

GameRepository (interface)
  ├── SqliteGameRepository    → SQLite (Phase 1)
  └── CloudGameRepository     → PostgreSQL / Firestore 등 (Phase 2+)
```

- Spring Profile (`local` / `prod`)로 구현체 자동 선택
- `application-local.properties` → SQLite, 로컬 스토리지
- `application-prod.properties` → 클라우드 DB, 클라우드 스토리지

### CI/CD

- **GitHub Actions**: main 브랜치 push 시 빌드 + 테스트 자동 실행
- Phase 1: 로컬 실행 (Actions는 빌드/테스트만 수행)
- Phase 2+: Actions에서 클라우드 서버로 배포 (SSH 또는 Docker 방식)
- Docker Compose로 로컬/클라우드 환경 통일

---

## 5. 기능 요구사항

### Phase 1: 인프라 기초

#### [서버] StorageService 추상화
- `StorageService` 인터페이스 정의: `upload()`, `getUrl()`, `delete()`
- `LocalStorageService`: 로컬 파일 저장, HTTP로 직접 서빙 (영구 URL)
- `CloudStorageService`: S3 / Firebase Storage 등 (Phase 2+에서 구현)
- Spring Profile로 구현체 자동 선택
- **해결**: 기존 signed URL 15분 만료 문제 → 로컬 환경에서는 영구 URL

#### [서버] DB Repository 추상화
- `GameRepository`, `WallpaperRepository` 인터페이스 정의
- `SqliteGameRepository`: SQLite로 게임 메타데이터 영속화
- `CloudGameRepository`: PostgreSQL / Firestore 등 (Phase 2+에서 구현)
- **해결**: 기존 메모리 Map → DB 이전, 서버 재시작 후 데이터 유지

#### [서버] Docker Compose 환경 구성
- `docker-compose.yml`: Spring Boot 서버 + SQLite 로컬 환경
- `docker-compose.prod.yml`: 클라우드 서버 환경 (Phase 2+)

---

### Phase 2: AI 범용 크롤러 + 관리자 페이지

#### [AI 핵심] AI 범용 크롤러 (Universal AI Crawler)

**개요**: 게임별 크롤러 클래스를 직접 코딩하는 대신, DB에 URL만 등록하면 AI가 페이지를 분석하여 파싱 전략을 자동 생성합니다.

**동작 흐름**:
```
1. 관리자가 게임 등록 (이름 + 배경화면 페이지 URL)
         ↓
2. 서버가 해당 URL의 HTML + Selenium 스크린샷을 Claude API에 전달
         ↓
3. Claude가 페이지 구조 분석 → 파싱 전략(JSON) 생성
         ↓
4. 파싱 전략을 DB에 저장
         ↓
5. GenericCrawlerExecutor가 전략을 읽어 크롤링 수행
         ↓
6. 크롤링 실패 시 → CrawlerStatus.FAILED → 재분석 트리거
```

**파싱 전략 스키마 (DB 저장)**:
```json
{
  "requiresJavaScript": true,
  "preActions": [
    { "type": "click", "selector": ".popup-skip-btn", "optional": true }
  ],
  "paginationType": "button_click",
  "nextPageSelector": ".next-page-btn",
  "stopCondition": "selector_has_class:.next-page-btn:disabled",
  "imageSelector": ".wallpaper-item img",
  "imageAttribute": "src",
  "imageType": "mobile",
  "waitMs": 2000,
  "analysisVersion": 1,
  "lastAnalyzedAt": "2026-03-13T00:00:00Z"
}
```

**기존 크롤러 교체**:
- 기존 게임별 서비스 클래스 6개 → `GenericCrawlerExecutor` 하나로 교체
- 기존 6개 게임도 AI 재분석하여 전략 DB 등록

**실패 처리**:
- 연속 N회 실패 → `CrawlerStatus.FAILED` 상태 변경
- 관리자 UI에서 수동 재분석 트리거 가능

#### [AI] 배경화면 자동 태그 생성
- 크롤링 파이프라인에서 Claude API로 이미지 태그 자동 생성
  - 예: `dark`, `landscape`, `character`, `blue-tone`
- DB 메타데이터에 태그 저장
- 클라이언트에서 태그 기반 필터링 UI 제공

#### [AI] 배경화면 AI 추천
- 사용자 좋아요 기록 기반으로 유사한 배경화면 추천
- Claude API 활용: 태그 유사도 기반 추천
- 홈 화면에 "추천 배경화면" 섹션 추가

#### [서버] 관리자 페이지 (Admin Dashboard)

Thymeleaf 기반 웹 UI, 4개 페이지 구성.

**① 대시보드 (`/admin`)**
- 전체 게임 수 / 총 배경화면 수 / 마지막 크롤링 시각 요약 카드
- 크롤러 상태별 게임 수 (ACTIVE / UPDATING / FAILED)
- 최근 크롤링 로그 타임라인

**② 게임 목록 (`/admin/games`)**
- 게임명 / 등록 URL / 배경화면 수 / 마지막 크롤링 시각 / 상태 배지
- 게임별 액션: `크롤링 실행` / `재분석` / `활성화·비활성화` / `삭제`

**③ 게임 등록 (`/admin/games/new`)**
- 게임명 + 배경화면 페이지 URL 입력
- `AI 분석 시작` → 진행 상태 실시간 표시 (polling)
  - "페이지 접속 중..." → "HTML 분석 중..." → "전략 생성 완료"
- 분석된 파싱 전략 JSON 미리보기 (수동 수정 가능)
- `저장 및 크롤링 시작` 버튼

**④ 게임 상세 (`/admin/games/{id}`)**
- 게임 기본 정보 (이름, URL, 등록일, 상태)
- **배경화면 현황 탭**: 썸네일 갤러리, 해상도별·태그별 분류, 개별 삭제
- **파싱 전략 탭**: 전략 JSON 에디터 (수동 수정), 분석 이력 (버전별 보관)
- **크롤링 로그 탭**: 실행 이력 (시각 / 수집 수 / 성공·실패 / 오류 메시지)

---

### Phase 3: 리팩토링

#### [클라이언트] 로컬 캐시 추가
- 게임 목록 / 배경화면 메타데이터 로컬 캐싱 (Hive 또는 SharedPreferences)
- 앱 재시작 시 빠른 초기 로딩

#### [클라이언트] 에러 처리 구조화
- 문자열 매칭 기반 에러 처리 → 에러 코드 시스템으로 교체

#### [클라이언트] 페이지네이션 개선
- Firebase Storage 전체 리스트 로드 → 서버 API 페이징으로 교체

#### [공통] 테스트 코드 추가
- 서버: 크롤러 서비스 단위 테스트, Repository 통합 테스트
- 클라이언트: Provider 단위 테스트

---

### Phase 4: 멀티플랫폼 확장

#### [플랫폼] Flutter 데스크탑 지원 (Windows / macOS / Linux)
- Flutter Desktop 빌드 활성화
- 플랫폼별 배경화면 설정 API (현재 `async_wallpaper`는 모바일 전용 → 교체 필요)
  - Windows: `SystemParametersInfo` Win32 API
  - macOS: `NSWorkspace.setDesktopImageURL`
  - Linux: `gsettings` / `feh`
- 데스크탑 전용 와이드 UI 레이아웃

#### [기능] 해상도별 이미지 추천
- 크롤링 시점에 이미지 해상도 메타데이터 저장 (width, height)
- 클라이언트 기기 해상도 감지 → 서버에 전달
- 서버가 해상도에 맞는 이미지 우선 반환
  - 모바일: 1080×1920 / FHD: 1920×1080 / QHD / 4K

#### [기능] PC 배경화면 이미지 수집
- 기존 크롤러: 세로형(모바일) 이미지만 필터링 → 가로형(데스크탑)도 수집
- AI 크롤러 파싱 전략 스키마에 `imageType` 필드 추가 (`mobile` / `desktop` / `both`)

#### [기능] 좋아요 / 즐겨찾기 (Should Have)
- 배경화면 좋아요 버튼 추가
- 사용자별 좋아요 목록 DB 저장
- 좋아요 목록 탭 추가
- AI 추천의 피드백 데이터로 활용

#### [AI] 배경화면 설명 자동 생성 (Should Have)
- 크롤링 파이프라인에서 Claude Vision으로 이미지 설명 자동 생성
  - 예: "파란 하늘 아래 펼쳐진 몬드 도시 전경"
- DB에 저장, 클라이언트 상세 화면에 표시

#### [기능] 라이브 배경화면 (Could Have - 복잡도 높음)
- 1단계: 이미지 슬라이드쇼 (일정 주기 자동 교체)
- 2단계: Android `WallpaperService` 기반 라이브 배경화면
- Windows: DreamScene 방식 (구현 복잡도 높음, 우선순위 낮음)

#### [기능] 기타 (Could Have)
- 신규 게임 추가 (블루 아카이브, 붕괴3rd 등) — Admin UI에서 URL 등록만으로 가능
- 자연어 검색 ("파란색 배경", "캐릭터 없는 풍경")
- iOS 지원

---

## 6. 배포 전략

### Phase 1: 로컬 실행
```
개발자 PC
├── Spring Boot 서버 (Docker Compose)
│   ├── SQLite (gamepaper.db)
│   └── 로컬 이미지 스토리지 (/storage/images/)
└── Flutter 앱 (Android 기기/에뮬레이터)
    └── 서버 API 호출 (로컬 IP)
```
- GitHub Actions: push 시 빌드 + 테스트만 수행

### Phase 2+: 클라우드 전환 (시점 미정)
```
push to main
    └── GitHub Actions
        ├── 빌드 & 테스트
        └── 클라우드 서버 자동 배포
            ├── 서버 플랫폼 (미정: AWS EC2 / Railway / Render 등)
            │   ├── DB (미정: PostgreSQL / Firestore 등)
            │   └── 스토리지 (미정: S3 / Firebase Storage / R2 등)
            └── 앱: 서버 URL 환경변수만 변경
```
- `application-prod.properties` 구현체만 교체, 앱 코드 변경 없음

---

## 7. 비기능 요구사항

| 항목 | 기준 |
|------|------|
| 이미지 로딩 | BlurHash 미리보기 유지 |
| 크롤링 주기 | 6시간마다 자동 실행 |
| AI 파싱 분석 | URL 등록 후 전략 생성 60초 이내 |
| AI 추천 응답 | 3초 이내 |
| 이미지 URL | 만료 없는 영구 URL |
| 환경 이식성 | properties 파일 교체만으로 로컬 ↔ 클라우드 전환 가능 |
| 보안 | API Key 환경변수 관리, Admin UI 인증 |
| 플랫폼 | Android (Phase 1~3) → Windows/macOS/Linux 추가 (Phase 4) |

---

## 8. API 설계

### 클라이언트용
| 엔드포인트 | 설명 |
|-----------|------|
| `GET /api/games` | 게임 목록 조회 |
| `GET /api/wallpapers/{game}` | 게임별 배경화면 목록 (tags, description 포함) |
| `GET /api/wallpapers/search?tags=dark,landscape` | 태그 기반 검색 |
| `GET /api/wallpapers/recommended` | AI 추천 배경화면 목록 |
| `POST /api/wallpapers/{id}/like` | 좋아요 |

### 관리자용 (Admin UI)
| 엔드포인트 | 설명 |
|-----------|------|
| `GET /admin` | 대시보드 |
| `GET /admin/games` | 게임 목록 + 크롤러 상태 |
| `GET /admin/games/new` | 게임 등록 폼 |
| `POST /admin/games` | 게임 등록 → AI 분석 자동 실행 |
| `GET /admin/games/{id}` | 게임 상세 (배경화면 갤러리 / 전략 / 로그 탭) |
| `POST /admin/games/{id}/analyze` | 파싱 전략 재분석 트리거 |
| `GET /admin/games/{id}/analyze/status` | AI 분석 진행 상태 조회 (polling) |
| `POST /admin/games/{id}/crawl` | 수동 크롤링 실행 |
| `PUT /admin/games/{id}/strategy` | 파싱 전략 수동 수정 |
| `PUT /admin/games/{id}/toggle` | 게임 활성화/비활성화 |
| `DELETE /admin/games/{id}` | 게임 및 수집 이미지 삭제 |
| `DELETE /admin/games/{id}/wallpapers/{wallpaperId}` | 배경화면 개별 삭제 |

---

## 9. 미결 사항

**AI 크롤러**
- [ ] AI 분석 방식: HTML 텍스트만 전달 vs Selenium 스크린샷도 함께 전달
- [ ] 파싱 전략 실패 시 재분석: 자동 트리거 vs 관리자 수동

**인프라**
- [ ] Flutter 상태관리: Provider 유지 vs Riverpod 마이그레이션
- [ ] Phase 2+ 클라우드 서버 플랫폼 (AWS EC2 / Railway / Render 등)
- [ ] Phase 2+ DB (PostgreSQL / Firestore 등)
- [ ] Phase 2+ 이미지 스토리지 (S3 / Firebase Storage / R2 등)

**멀티플랫폼**
- [ ] 데스크탑 배경화면 설정 라이브러리 (Win32 직접 호출 vs 서드파티 플러그인)
- [ ] 라이브 배경화면 구현 방식 및 우선순위
- [ ] 추가할 게임 목록 확정 (블루 아카이브, 붕괴3rd 등)
