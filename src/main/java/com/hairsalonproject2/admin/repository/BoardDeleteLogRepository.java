package com.hairsalonproject2.admin.repository;

import com.hairsalonproject2.admin.entity.BoardDeleteLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * BoardDeleteLogRepository
 * 최근 강제 삭제 기록 조회
 */
public interface BoardDeleteLogRepository extends JpaRepository<BoardDeleteLog, Long> {

 List<BoardDeleteLog> findTop10ByOrderByCreatedAtDesc();
}
