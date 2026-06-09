package com.mysite.jaminboard;

import org.springframework.boot.security.autoconfigure.web.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(PathRequest.toH2Console()).permitAll()
				.requestMatchers(
						"/", "/question/list", "/question/detail/**",
						"/bootstrap.min.css", "/style.css", "/error",
						"/user/signup", "/user/login", "/admin/login").permitAll()
				.requestMatchers("/admin/**").hasRole("ADMIN")
				.requestMatchers("/answer/**").hasRole("ADMIN")
				.anyRequest().authenticated())
			.csrf(csrf -> csrf.ignoringRequestMatchers(PathRequest.toH2Console()))
			.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
			.formLogin(form -> form
				.loginPage("/user/login")
				.loginProcessingUrl("/user/login")
				.defaultSuccessUrl("/question/list"))
			.logout(logout -> logout
				.logoutUrl("/user/logout")
				.logoutSuccessUrl("/question/list")
				.invalidateHttpSession(true));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
