# Sprint 5 배포 가이드

## 자동 검증 완료

- ✅ Flutter analyze 에러 없음 (5개 info 경고는 기존 코드 — 신규 코드 에러 없음)

> 서버 전체 테스트(TaggingServiceTest, BatchTaggingServiceTest, WallpaperSearchApiTest, LikeApiTest, RecommendationServiceTest)는 서버 재빌드 후 수동으로 실행해야 합니다.

## 수동 검증 필요

### 1. 서버 재빌드 (신규 엔티티 user_likes 테이블 생성 포함)

```bash
docker compose up --build
```

JPA auto-ddl=update 설정에 의해 `user_likes` 테이블이 자동 생성됩니다.

### 2. 서버 전체 테스트 실행

```bash
cd server && ./gradlew test 2>&1 | tail -40
```

예상: `BUILD SUCCESSFUL`, 전체 테스트 PASS

### 3. API 동작 확인

```bash
# 태그 목록 (기존 이미지에 태그가 있는 경우)
curl http://localhost:8080/api/tags

# 태그 기반 검색 (dark 태그 포함 배경화면)
curl "http://localhost:8080/api/wallpapers/search?tags=dark"

# 추천 API (좋아요 이력 없으면 빈 배열)
curl -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/recommended

# 좋아요 토글
curl -X POST -H "device-id: test-device-001" http://localhost:8080/api/wallpapers/1/like
```

### 4. 기존 이미지 배치 태깅 실행

서버 기동 후 관리자 UI에서 배치 태깅을 실행하거나 API를 직접 호출합니다:

```bash
curl -X POST http://localhost:8080/admin/api/tagging/batch
```

- Claude API 키가 설정된 경우: 실제 태그 생성
- API 키 미설정 시: 태그 생성 건너뜀 (빈 목록 반환)

### 5. Flutter 앱 실행 확인

```bash
cd /d/work/GamePaper/client
flutter run
```

확인 항목:
- ⬜ 배경화면 화면 상단에 태그 필터 칩이 표시된다
- ⬜ 태그 선택 시 필터링된 배경화면이 표시된다
- ⬜ 배경화면 카드 우하단에 좋아요 버튼(하트 아이콘 + 카운트)이 표시된다
- ⬜ 좋아요 버튼 탭 시 하트 아이콘이 빨간색으로 변경되고 카운트가 증가한다
- ⬜ 홈 화면에서 좋아요 이력이 있는 경우 "추천 배경화면" 섹션이 표시된다

### 6. 관리자 UI 태그 표시 확인

브라우저에서 `http://localhost:8080/admin/games/{id}` 접속:
- ⬜ 배경화면 탭에서 각 이미지 하단에 태그가 표시된다 (배치 태깅 실행 후)
- ⬜ "전체 태그 생성" 버튼 클릭 시 202 Accepted 응답과 함께 백그라운드 처리가 시작된다

## Flutter 변경 사항 요약

### Task 6 — 모델 및 API 레이어
- `lib/models/game.dart` — `Wallpaper` 클래스에 `tags: List<String>`, `likeCount: int` 필드 추가
- `lib/config/api_config.dart` — `searchUrl`, `tagsUrl`, `recommendedUrl`, `likeUrl` URL 메서드 추가
- `lib/repositories/game_repository.dart` — `fetchTags`, `searchByTags`, `fetchRecommended`, `toggleLike` 메서드 추가

### Task 7 — Provider 및 UI 레이어
- `lib/utils/device_id.dart` — SharedPreferences 기반 기기 ID 생성/조회 유틸리티
- `lib/providers/tag_filter_provider.dart` — 태그 필터 상태 Provider
- `lib/providers/recommendation_provider.dart` — 추천 배경화면 상태 Provider
- `lib/widgets/wallpaper/tag_filter_chips.dart` — 태그 필터 칩 위젯
- `lib/widgets/home/recommended_section.dart` — 추천 배경화면 섹션 위젯
- `lib/widgets/wallpaper/wallpaper_card.dart` — 좋아요 버튼(하트 아이콘 + 카운트) 추가
- `lib/screens/wallpaper_screen.dart` — MultiProvider로 교체, TagFilterChips 통합
- `lib/screens/home_screen.dart` — RecommendationProvider 주입, RecommendedSection 삽입
- `pubspec.yaml` — `shared_preferences: ^2.2.0` 의존성 추가
