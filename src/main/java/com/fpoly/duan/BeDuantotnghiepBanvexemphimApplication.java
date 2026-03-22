package com.fpoly.duan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan(basePackages = "com.fpoly.duan.config")
public class BeDuantotnghiepBanvexemphimApplication {

	public static void main(String[] args) {
		SpringApplication.run(BeDuantotnghiepBanvexemphimApplication.class, args);
	}

}
