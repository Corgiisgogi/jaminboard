package com.mysite.jaminboard.user;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mysite.jaminboard.DataNotFoundException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class UserService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public SiteUser create(String username, String email, String password) {
		SiteUser user = new SiteUser();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(password));
		user.setRole(UserRole.USER);
		user.setCreateDate(LocalDateTime.now());
		return this.userRepository.save(user);
	}

	public SiteUser getUser(String username) {
		return this.userRepository.findByUsername(username)
				.orElseThrow(() -> new DataNotFoundException("siteuser not found"));
	}
}
