package com.hairsalonproject2.catalog;

import com.hairsalonproject2.common.constant.MemberRole;
import com.hairsalonproject2.common.constant.MemberStatus;
import com.hairsalonproject2.common.constant.PaymentMethod;
import com.hairsalonproject2.common.constant.ReservationStatus;
import com.hairsalonproject2.designer.entity.Designer;
import com.hairsalonproject2.member.entity.Member;
import com.hairsalonproject2.reservation.entity.Reservation;
import com.hairsalonproject2.review.entity.Review;
import com.hairsalonproject2.salon.entity.Salon;
import com.hairsalonproject2.salonservice.entity.SalonService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** Never connects to or clears the application's normal development database. */
@Tag("mysql")
@SpringBootTest(properties = "spring.config.location=classpath:/application-catalog-test.properties")
abstract class CatalogMySqlTestSupport {
 @Autowired protected JdbcTemplate jdbc;
 @Autowired protected PlatformTransactionManager transactionManager;
 @Autowired protected EntityManagerFactory entityManagerFactory;
 @PersistenceContext protected EntityManager entityManager;

 @BeforeEach
 void resetOnlyExplicitlyIsolatedDatabase() {
  jdbc.execute((ConnectionCallback<Void>) connection -> {
   assertThat(connection.getCatalog()).as("only the isolated catalog_test schema may be cleared")
     .isEqualTo("catalog_test");
   assertThat(connection.getMetaData().getURL())
     .matches("jdbc:mysql://(127\\.0\\.0\\.1|localhost):[0-9]+/catalog_test(?:\\?.*)?")
     .doesNotContain(":3306/");
   var tables = new ArrayList<String>();
   try (var statement = connection.createStatement();
        var rows = statement.executeQuery("select table_name from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE'")) {
    while (rows.next()) tables.add(rows.getString(1));
   }
   try (var statement = connection.createStatement()) {
    statement.execute("set foreign_key_checks = 0");
    try {
     for (String table : tables) statement.execute("truncate table `" + table.replace("`", "``") + "`");
    } finally {
     statement.execute("set foreign_key_checks = 1");
    }
   }
   return null;
  });
  statistics().clear();
 }

 protected <T> T tx(Function<EntityManager, T> action) {
  var transaction = new TransactionTemplate(transactionManager);
  transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
  transaction.setTimeout(15);
  return transaction.execute(status -> action.apply(entityManager));
 }

 protected Statistics statistics() {
  return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
 }

 protected Fixture fixture() {
  return tx(em -> {
   Member customer = member("customer", MemberRole.USER);
   Salon first = salon("First salon", "Seoul address", "Seoul road", "4.50");
   Salon second = salon("Second salon", "Busan address", "Busan road", "4.00");
   Designer designer = designer(first, "Designer");
   SalonService service = service(first, "Cut", 20000, 30);
   return new Fixture(first.getSalonId(), second.getSalonId(), designer.getDesignerId(), service.getServiceId(), customer.getMemberId());
  });
 }

 protected Member member(String id, MemberRole role) {
  Member member = Member.builder().memberId(id).password("test-password").name(id)
    .role(role).status(MemberStatus.ACTIVE).build();
  entityManager.persist(member);
  return member;
 }

 protected Salon salon(String name, String address, String road, String rating) {
  Salon salon = Salon.builder().name(name).address(address).roadAddress(road)
    .averageRating(new BigDecimal(rating)).build();
  entityManager.persist(salon);
  return salon;
 }

 protected Designer designer(Salon salon, String name) {
  Designer designer = Designer.builder().salon(salon).name(name).careerYears(5).build();
  entityManager.persist(designer);
  return designer;
 }

 protected SalonService service(Salon salon, String name, int price, int duration) {
  SalonService service = SalonService.builder().salon(salon).name(name).price(price).duration(duration).build();
  entityManager.persist(service);
  return service;
 }

 protected Reservation reservation(Fixture fixture, ReservationStatus status) {
  return reservation(entityManager.getReference(Member.class, fixture.memberId()),
    entityManager.getReference(Designer.class, fixture.designerId()),
    entityManager.getReference(SalonService.class, fixture.serviceId()), status);
 }

 protected Reservation reservation(Member member, Designer designer, SalonService service, ReservationStatus status) {
  Reservation reservation = Reservation.builder().member(member).designer(designer).salonService(service)
    .reservationDate(LocalDate.now().plusDays(10)).reservationTime(LocalTime.of(10, 0))
    .status(status).totalPrice(service.getPrice()).paymentMethod(PaymentMethod.CARD).build();
  entityManager.persist(reservation);
  return reservation;
 }

 protected Review review(Reservation reservation, Designer designer, int rating, String content) {
  Review review = Review.builder().reservation(reservation).member(reservation.getMember())
    .designer(designer).rating((byte) rating).content(content).build();
  entityManager.persist(review);
  return review;
 }

 protected record Fixture(int salonId, int otherSalonId, int designerId, int serviceId, String memberId) { }
}
