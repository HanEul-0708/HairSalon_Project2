package com.hairsalonproject2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@EnableJpaAuditing
@SpringBootApplication
public class HairSalonProject2Application extends SpringBootServletInitializer {

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(HairSalonProject2Application.class);
	}

	public static void main(String[] args) {
		SpringApplication.run(HairSalonProject2Application.class, args);
	}
}