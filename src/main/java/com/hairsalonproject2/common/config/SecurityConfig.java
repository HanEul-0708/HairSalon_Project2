package com.hairsalonproject2.common.config;

import org.springframework.web.filter.HiddenHttpMethodFilter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import com.hairsalonproject2.member.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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
                .httpBasic(httpBasic -> httpBasic.disable())   // 이 줄 추가
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/upload/**",
                                "/error/**",

                                // 회원
                                "/members/signup",
                                "/members/login",

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
                        .requestMatchers(HttpMethod.GET, "/files/images/**", "/files/download/**").permitAll()
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
                // ✅ 권한 없는 사용자가 접근했을 때 이동할 페이지
                .exceptionHandling(exception -> exception
                        .accessDeniedPage("/access-denied")
                );


        return http.build();
    }

    /**
     * HTML form에서 _method=delete 사용 가능하게 하는 필터
     * 게시글 삭제 버튼 동작에 필요
     */
    @Bean
    public HiddenHttpMethodFilter hiddenHttpMethodFilter() {
        return new HiddenHttpMethodFilter();
    }

}
