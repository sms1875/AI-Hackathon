---
name: GamePaper 프로젝트 개요
description: GamePaper 프로젝트의 핵심 기술 결정, Phase 구조, 주요 특성 요약
type: project
---

GamePaper는 게임 공식 배경화면을 AI 기반으로 자동 수집하여 모바일/데스크탑에 적용하는 앱.

**Why:** 기존 6개 게임별 개별 크롤러의 유지보수 부담을 AI 범용 크롤러로 해소하고, URL 등록만으로 신규 게임을 추가 가능하게 하기 위함.

**How to apply:**
- 4개 Phase, 8 스프린트 (16주) 계획
- Phase 1: 인프라 기초 (Sprint 1-2) - StorageService/DB 추상화, Docker, 기존 크롤러 마이그레이션
- Phase 2: AI 크롤러 + 관리자 UI (Sprint 3-5) - Claude API 파싱 전략 생성, Thymeleaf 관리자 페이지, 태그/추천
- Phase 3: 리팩토링 (Sprint 6) - 클라이언트 캐시, 에러 구조화, 테스트
- Phase 4: 멀티플랫폼 (Sprint 7-8) - 데스크탑 지원, 해상도별 추천, 좋아요
- 기술 스택: Spring Boot 3.x (Java 21), SQLite (Phase 1), Flutter (Dart), Claude API, Selenium + Jsoup
- 로드맵 생성일: 2026-03-13, 아직 코드 없는 상태에서 시작
