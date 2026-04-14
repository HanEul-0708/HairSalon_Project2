package com.hairsalonproject2.board.repository;

import com.hairsalonproject2.board.entity.BoardFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoardFileRepository extends JpaRepository<BoardFile, Long> {

    @Query("""
            select boardFile
            from BoardFile boardFile
            join fetch boardFile.board board
            where boardFile.savedName = :savedName
            """)
    Optional<BoardFile> findBySavedNameWithBoard(@Param("savedName") String savedName);
}
