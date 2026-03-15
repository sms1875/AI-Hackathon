-- 6개 게임 초기 데이터 (중복 삽입 방지: INSERT OR IGNORE)
INSERT OR IGNORE INTO games (id, name, url, status, created_at) VALUES
(1, '원신', 'https://www.hoyolab.com/wallpaper', 'ACTIVE', datetime('now')),
(2, '마비노기', 'https://mabinogi.nexon.com/web/contents/wallPaper', 'ACTIVE', datetime('now')),
(3, '메이플스토리 모바일', 'https://m.maplestory.nexon.com/Media/WallPaper', 'ACTIVE', datetime('now')),
(4, 'NIKKE', 'https://www.nikke-kr.com/fansite.html', 'ACTIVE', datetime('now')),
(5, '파이널판타지 XIV', 'https://na.finalfantasyxiv.com/lodestone/special/fankit/desktop_wallpaper/', 'ACTIVE', datetime('now')),
(6, '검은사막', 'https://www.kr.playblackdesert.com/ko-KR/About/WallPaper', 'ACTIVE', datetime('now'));
