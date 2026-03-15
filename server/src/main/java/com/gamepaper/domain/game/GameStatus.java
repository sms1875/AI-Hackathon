package com.gamepaper.domain.game;

public enum GameStatus {
    ACTIVE,
    UPDATING,
    FAILED,
    INACTIVE  // 관리자가 명시적으로 비활성화한 상태 (I-3 해소)
}
