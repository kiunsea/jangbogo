package com.jiniebox.jangbogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.File;

@SpringBootApplication
public class JangbogoApplication {

	public static void main(String[] args) {
		// Spring Boot 시작 전에 필요한 디렉토리 생성
		createRequiredDirectories();
		
		SpringApplication.run(JangbogoApplication.class, args);
	}

	/**
	 * 애플리케이션 실행에 필요한 디렉토리 생성
	 */
	private static void createRequiredDirectories() {
		String[] requiredDirs = {"db", "logs", "exports"};
		for (String dir : requiredDirs) {
			File directory = new File(dir);
			if (!directory.exists()) {
				boolean created = directory.mkdirs();
				if (created) {
					System.out.println("✓ 디렉토리 생성됨: " + directory.getAbsolutePath());
				}
			}
		}
	}

}
