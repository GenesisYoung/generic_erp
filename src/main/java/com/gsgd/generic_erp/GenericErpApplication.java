package com.gsgd.generic_erp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/** Spring Boot entry point for the Generic ERP backend service. */
@SpringBootApplication
@EnableCaching
public class GenericErpApplication {

	public static void main(String[] args) {
		SpringApplication.run(GenericErpApplication.class, args);
	}

}
