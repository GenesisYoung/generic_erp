package com.gsgd.generic_erp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = { GenericErpApplication.class })
class GenericErpApplicationTests {
	private Argon2PasswordEncoder passwordEncoder=new Argon2PasswordEncoder(64, 50, 2, 1 << 16, 4);
	@Test
	void contextLoads() {
		String password = passwordEncoder.encode("admin0904");
		System.err.println(password);
	}

}
