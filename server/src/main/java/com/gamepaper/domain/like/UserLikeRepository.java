package com.gamepaper.domain.like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserLikeRepository extends JpaRepository<UserLike, Long> {

    boolean existsByDeviceIdAndWallpaperId(String deviceId, Long wallpaperId);

    void deleteByDeviceIdAndWallpaperId(String deviceId, Long wallpaperId);

    List<UserLike> findAllByDeviceId(String deviceId);

    long countByWallpaperId(Long wallpaperId);

    /**
     * 특정 기기의 좋아요에서 가장 많이 등장한 태그를 반환합니다.
     * wallpaper의 tags 필드(JSON 배열)와 join하여 집계합니다.
     *
     * 실제 구현은 WallpaperRepository.findByIds()로 배경화면 조회 후
     * Java에서 태그 빈도 분석으로 처리합니다.
     */
    @Query("SELECT ul.wallpaperId FROM UserLike ul WHERE ul.deviceId = :deviceId ORDER BY ul.createdAt DESC")
    List<Long> findWallpaperIdsByDeviceId(@Param("deviceId") String deviceId);
}
