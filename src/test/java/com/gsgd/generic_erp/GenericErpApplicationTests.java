package com.gsgd.generic_erp;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.gsgd.generic_erp.repository.auth.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = { GenericErpApplication.class })
class GenericErpApplicationTests {
	// private Argon2PasswordEncoder passwordEncoder = new Argon2PasswordEncoder(64,
	// 50, 2, 1 << 16, 4);

	// @Test
	// void contextLoads() {
	// String password = passwordEncoder.encode("admin0904");
	// System.err.println(password);
	// }
	UserRepository repository = mock(UserRepository.class);
	// private JWTUtil jwtUtil = new JWTUtil(repository);

	// @Test
	// void testToken() {
	// boolean isValid = jwtUtil.isValid(0,
	// "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJnZW5lc2lzeW91bmciLCJpYXQiOjE3ODIyMDA2OTQsImV4cCI6MTc4MjgwNTQ5NH0.xKPHvnfihu90cwai1XnVZ6v0i87pJOh3MLiqy4MKxoo");
	// System.err.println("The code is valid " + isValid);
	// }

}