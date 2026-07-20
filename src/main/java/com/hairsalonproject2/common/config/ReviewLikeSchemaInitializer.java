package com.hairsalonproject2.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewLikeSchemaInitializer implements ApplicationRunner {

 private final JdbcTemplate jdbcTemplate;

 @Override
 public void run(ApplicationArguments args) {
  if (!columnExists("review", "like_count")) {
   jdbcTemplate.execute("""
		   ALTER TABLE review
		   ADD COLUMN like_count INT NULL
		   """);
  }
  jdbcTemplate.execute("""
		  UPDATE review
		  SET like_count = 0
		  WHERE like_count IS NULL
		  """);
  jdbcTemplate.execute("""
		  CREATE TABLE IF NOT EXISTS review_like (
		      review_like_id INT AUTO_INCREMENT PRIMARY KEY,
		      review_id INT NOT NULL,
		      member_id VARCHAR(30) NULL,
		      visitor_token VARCHAR(64) NULL,
		      created_at DATETIME(6),
		      CONSTRAINT uk_review_like_review_member UNIQUE (review_id, member_id),
		      CONSTRAINT uk_review_like_review_visitor UNIQUE (review_id, visitor_token),
		      CONSTRAINT fk_review_like_review FOREIGN KEY (review_id) REFERENCES review (review_id),
		      CONSTRAINT fk_review_like_member FOREIGN KEY (member_id) REFERENCES member (member_id)
		  )
		  """);
  if (!columnExists("review_like", "visitor_token")) {
   jdbcTemplate.execute("""
		   ALTER TABLE review_like
		   ADD COLUMN visitor_token VARCHAR(64) NULL
		   """);
  }
  jdbcTemplate.execute("""
		  ALTER TABLE review_like
		  MODIFY COLUMN member_id VARCHAR(30) NULL
		  """);
  if (!constraintExists("review_like", "uk_review_like_review_visitor")) {
   jdbcTemplate.execute("""
		   ALTER TABLE review_like
		   ADD CONSTRAINT uk_review_like_review_visitor UNIQUE (review_id, visitor_token)
		   """);
  }
 }

 private boolean columnExists(String tableName, String columnName) {
  Integer count = jdbcTemplate.queryForObject("""
		  SELECT COUNT(*)
		  FROM information_schema.columns
		  WHERE table_schema = DATABASE()
		    AND table_name = ?
		    AND column_name = ?
		  """, Integer.class, tableName, columnName);
  return count != null && count > 0;
 }

 private boolean constraintExists(String tableName, String constraintName) {
  Integer count = jdbcTemplate.queryForObject("""
		  SELECT COUNT(*)
		  FROM information_schema.table_constraints
		  WHERE table_schema = DATABASE()
		    AND table_name = ?
		    AND constraint_name = ?
		  """, Integer.class, tableName, constraintName);
  return count != null && count > 0;
 }
}
