package com.hairsalonproject2.common.config;

import com.hairsalonproject2.member.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정
 */
@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService customUserDetailsService;

    /**
     * 비밀번호 암호화 객체 등록
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 인증 제공자 등록
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Security 필터 체인 설정
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authenticationProvider(authenticationProvider())
                .csrf(Customizer.withDefaults())

                // 브라우저 기본 로그인 팝업 비활성화
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/reservations").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/reservations/*/status").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/salons/new", "/salons/*/edit").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/salons", "/salons/sync/kakao", "/salons/*/edit").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/salons/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/salon-services/new", "/salon-services/*/edit").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/salon-services", "/salon-services/*/edit", "/salon-services/*/delete").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/salon-services/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/reviews/*/images").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/reviews/*/images/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/reviews").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/reviews/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reviews/*").authenticated()
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/upload/**",
                                "/uploads/**",
                                "/files/images/**",
                                "/files/download/**",
                                "/error/**",

                                // 회원
                                "/members/signup",
                                "/members/login",
                                "/members/check-id",
                                "/members/check-email",
                                "/members/check-phone",

                                // 메인/살롱/디자이너/리뷰/게시판 테스트용 공개
                                "/salons",
                                "/salons/**",
                                "/designers",
                                "/designers/*",
                                "/salon-services",
                                "/salon-services/**",
                                "/reviews",
                                "/reviews/**",
                                "/boards/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reviews").authenticated()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/members/me/delete").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/members/login")
                        .loginProcessingUrl("/members/login")
                        .usernameParameter("memberId")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/members/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/members/logout")
                        .logoutSuccessUrl("/members/login?logout=true")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                // 권한이 없는 사용자가 접근했을 때 이동할 페이지
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/access-denied")
                );

        return http.build();
    }
}
