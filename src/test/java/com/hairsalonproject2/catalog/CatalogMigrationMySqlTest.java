package com.hairsalonproject2.catalog;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercises the shipped SQL only on a disposable schema inside the isolated MySQL instance. */
class CatalogMigrationMySqlTest extends CatalogMySqlTestSupport {
 enum StartingSchema { INITIAL_DDL, LEGACY_CASCADE, LEGACY_WITHOUT_DESIGNER_LIKES }

 private record ForeignKey(String table, String column, String parent, String parentColumn, String deleteRule) { }
 private static final List<ForeignKey> PROTECTED_KEYS = List.of(
   new ForeignKey("designer", "salon_id", "salon", "salon_id", "RESTRICT"),
   new ForeignKey("salon_service", "salon_id", "salon", "salon_id", "RESTRICT"),
   new ForeignKey("reservation", "designer_id", "designer", "designer_id", "RESTRICT"),
   new ForeignKey("reservation", "service_id", "salon_service", "service_id", "RESTRICT"),
   new ForeignKey("review", "designer_id", "designer", "designer_id", "RESTRICT"),
   new ForeignKey("designer_like", "designer_id", "designer", "designer_id", "CASCADE"),
   new ForeignKey("salon_like", "salon_id", "salon", "salon_id", "CASCADE"));

 @ParameterizedTest
 @EnumSource(StartingSchema.class)
 void migrationCanRunTwiceOnFreshAndLegacySchemasWithoutLosingRows(StartingSchema startingSchema) throws Exception {
  String bootstrap = Files.readString(Path.of("sql/HairSalon_Project2_table.sql"), StandardCharsets.UTF_8);
  int firstTable = bootstrap.indexOf("CREATE TABLE member");
  assertThat(firstTable).isPositive();
  // Never execute the bootstrap's user, grant, DROP DATABASE or USE commands.
  String tableDdl = bootstrap.substring(firstTable);
  assertThat(tableDdl).doesNotContain("salon_project_v2", "DROP DATABASE", "CREATE USER", "GRANT ALL");
  String migration = Files.readString(Path.of("sql/migrations/20260924_catalog_integrity.sql"), StandardCharsets.UTF_8);
  String schema = "catalog_migration_" + UUID.randomUUID().toString().replace("-", "");
  jdbc.execute((ConnectionCallback<Void>) connection -> {
   String original = connection.getCatalog();
   assertThat(original).isEqualTo("catalog_test");
   assertThat(connection.getMetaData().getURL()).doesNotContain(":3306/");
   assertThat(schema).matches("catalog_migration_[a-f0-9]{32}");
   execute(connection, "CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
   try {
    connection.setCatalog(schema);
    script(connection, tableDdl);
    if (startingSchema == StartingSchema.INITIAL_DDL) assertDeleteRules(connection);
    else {
     for (ForeignKey key : PROTECTED_KEYS) replaceForeignKey(connection, key, key.deleteRule().equals("RESTRICT") ? "CASCADE" : "RESTRICT");
     execute(connection, "ALTER TABLE salon DROP INDEX uq_salon_external, ADD CONSTRAINT uq_legacy_external UNIQUE(external_id)");
    }
    if (startingSchema == StartingSchema.LEGACY_WITHOUT_DESIGNER_LIKES) execute(connection, "DROP TABLE designer_like");
    seedHistory(connection, startingSchema != StartingSchema.LEGACY_WITHOUT_DESIGNER_LIKES);
    Map<String, List<List<Object>>> before = snapshot(connection, startingSchema != StartingSchema.LEGACY_WITHOUT_DESIGNER_LIKES);
    for (int run = 0; run < 2; run++) {
     script(connection, migration);
     assertDeleteRules(connection);
     assertThat(snapshot(connection, startingSchema != StartingSchema.LEGACY_WITHOUT_DESIGNER_LIKES)).isEqualTo(before);
    }
    for (String deletion : List.of("DELETE FROM salon WHERE salon_id = 1", "DELETE FROM designer WHERE designer_id = 1", "DELETE FROM salon_service WHERE service_id = 1")) {
     assertThatThrownBy(() -> execute(connection, deletion)).isInstanceOf(SQLIntegrityConstraintViolationException.class);
    }
    assertThat(snapshot(connection, startingSchema != StartingSchema.LEGACY_WITHOUT_DESIGNER_LIKES)).isEqualTo(before);
    execute(connection, "INSERT INTO salon(name,address,source_type,external_id) VALUES ('Other provider','Address','NAVER','external-1')");
    assertThatThrownBy(() -> execute(connection, "INSERT INTO salon(name,address,source_type,external_id) VALUES ('Duplicate','Address','KAKAO','external-1')"))
      .isInstanceOf(SQLIntegrityConstraintViolationException.class);
    long preservedDesignerLikes = startingSchema == StartingSchema.LEGACY_WITHOUT_DESIGNER_LIKES ? 0 : 1;
    assertThat(count(connection, "designer_like")).isEqualTo(preservedDesignerLikes);
    // Exercise the migrated constraints with an unreferenced designer and salon as well.
    execute(connection, "INSERT INTO salon(salon_id,name,address) VALUES (100,'Disposable salon','Address')");
    execute(connection, "INSERT INTO designer(designer_id,salon_id,name) VALUES (100,100,'Disposable designer')");
    execute(connection, "INSERT INTO designer_like(member_id,designer_id) VALUES ('customer',100)");
    execute(connection, "INSERT INTO salon_like(member_id,salon_id) VALUES ('customer',100)");
    execute(connection, "DELETE FROM designer WHERE designer_id = 100");
    execute(connection, "DELETE FROM salon WHERE salon_id = 100");
    assertThat(count(connection, "designer_like")).isEqualTo(preservedDesignerLikes);
    assertThat(count(connection, "salon_like")).isOne();
    assertThat(count(connection, "member")).isOne();
    assertThat(count(connection, "reservation")).isEqualTo(3);
    assertThat(count(connection, "review")).isOne();
   } finally {
    connection.setCatalog(original);
    execute(connection, "DROP DATABASE `" + schema + "`");
   }
   return null;
  });
 }

 private static void script(Connection connection, String sql) {
  ScriptUtils.executeSqlScript(connection, new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
 }

 private static void seedHistory(Connection connection, boolean designerLikes) throws SQLException {
  execute(connection, "INSERT INTO member(member_id,password,name,role,status) VALUES ('customer','test','Customer','USER','ACTIVE')");
  execute(connection, "INSERT INTO salon(salon_id,name,address,source_type,external_id) VALUES (1,'Original salon','Original address','KAKAO','external-1')");
  execute(connection, "INSERT INTO designer(designer_id,salon_id,name) VALUES (1,1,'Original designer')");
  execute(connection, "INSERT INTO salon_service(service_id,salon_id,name,price,duration) VALUES (1,1,'Original cut',20000,30)");
  int id = 0;
  for (String status : List.of("RESERVED", "CANCELLED", "COMPLETED")) {
   id++;
   execute(connection, "INSERT INTO reservation(reservation_id,member_id,designer_id,service_id,reservation_date,reservation_time,status,total_price) VALUES (" + id + ",'customer',1,1,'2030-01-01','10:00:00','" + status + "',20000)");
  }
  execute(connection, "INSERT INTO review(reservation_id,member_id,designer_id,rating,content) VALUES (3,'customer',1,5,'Preserve this review')");
  execute(connection, "INSERT INTO reservation_slot(reservation_id,designer_id,reservation_date,slot_time) VALUES (1,1,'2030-01-01','10:00:00')");
  execute(connection, "INSERT INTO salon_like(member_id,salon_id) VALUES ('customer',1)");
  if (designerLikes) execute(connection, "INSERT INTO designer_like(member_id,designer_id) VALUES ('customer',1)");
 }

 private static Map<String, List<List<Object>>> snapshot(Connection connection, boolean designerLikes) throws SQLException {
  Map<String, List<List<Object>>> rows = new LinkedHashMap<>();
  for (String table : List.of("member", "salon", "designer", "salon_service", "reservation", "review", "reservation_slot", "salon_like")) {
   rows.put(table, query(connection, "SELECT * FROM " + table + " ORDER BY 1"));
  }
  if (designerLikes) rows.put("designer_like", query(connection, "SELECT * FROM designer_like ORDER BY 1"));
  return rows;
 }

 private static void assertDeleteRules(Connection connection) throws SQLException {
  for (ForeignKey key : PROTECTED_KEYS) {
   try (var statement = connection.prepareStatement("SELECT r.DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS r JOIN information_schema.KEY_COLUMN_USAGE k ON k.CONSTRAINT_SCHEMA=r.CONSTRAINT_SCHEMA AND k.TABLE_NAME=r.TABLE_NAME AND k.CONSTRAINT_NAME=r.CONSTRAINT_NAME WHERE k.TABLE_SCHEMA=DATABASE() AND k.TABLE_NAME=? AND k.COLUMN_NAME=? AND k.REFERENCED_TABLE_NAME=?")) {
    statement.setString(1, key.table());
    statement.setString(2, key.column());
    statement.setString(3, key.parent());
    try (var result = statement.executeQuery()) {
     assertThat(result.next()).as(key.table() + "." + key.column()).isTrue();
     assertThat(result.getString(1)).as(key.table() + "." + key.column()).isEqualTo(key.deleteRule());
     assertThat(result.next()).isFalse();
    }
   }
  }
 }

 private static void replaceForeignKey(Connection connection, ForeignKey key, String rule) throws SQLException {
  String constraint;
  try (var statement = connection.prepareStatement("SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? AND COLUMN_NAME=? AND REFERENCED_TABLE_NAME=?")) {
   statement.setString(1, key.table());
   statement.setString(2, key.column());
   statement.setString(3, key.parent());
   try (var row = statement.executeQuery()) {
    assertThat(row.next()).isTrue();
    constraint = row.getString(1).replace("`", "``");
   }
  }
  execute(connection, "ALTER TABLE `" + key.table() + "` DROP FOREIGN KEY `" + constraint + "`, ADD CONSTRAINT `legacy_" + key.table() + "_" + key.column() + "` FOREIGN KEY (`" + key.column() + "`) REFERENCES `" + key.parent() + "` (`" + key.parentColumn() + "`) ON DELETE " + rule);
 }

 private static List<List<Object>> query(Connection connection, String sql) throws SQLException {
  List<List<Object>> result = new ArrayList<>();
  try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
   while (rows.next()) {
    List<Object> row = new ArrayList<>();
    for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) row.add(rows.getObject(column));
    result.add(row);
   }
  }
  return result;
 }

 private static void execute(Connection connection, String sql) throws SQLException {
  try (var statement = connection.createStatement()) { statement.execute(sql); }
 }

 private static long count(Connection connection, String table) throws SQLException {
  try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
   assertThat(rows.next()).isTrue();
   return rows.getLong(1);
  }
 }
}
