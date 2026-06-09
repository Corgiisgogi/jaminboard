package com.mysite.jaminboard;

import java.time.LocalDateTime;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mysite.jaminboard.user.SiteUser;
import com.mysite.jaminboard.user.UserRepository;
import com.mysite.jaminboard.user.UserRole;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(String... args) {
		if (this.userRepository.findByUsername("admin").isEmpty()) {
			SiteUser admin = new SiteUser();
			admin.setUsername("admin");
			admin.setPassword(this.passwordEncoder.encode("admin1234"));
			admin.setEmail("admin@jaminboard.com");
			admin.setRole(UserRole.ADMIN);
			admin.setCreateDate(LocalDateTime.now());
			this.userRepository.save(admin);
		}
	}
}
