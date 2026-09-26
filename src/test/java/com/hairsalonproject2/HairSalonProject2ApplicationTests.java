package com.hairsalonproject2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("mysql")
@SpringBootTest(properties = "spring.config.location=classpath:/application-catalog-test.properties")
class HairSalonProject2ApplicationTests {

 @Test
 void contextLoads() {
 }

}
