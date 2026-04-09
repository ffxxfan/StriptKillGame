package com.example.striptkillgamedemo2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口。
 * <p>
 * 剧本杀（Script Kill）Web 游戏后端 Spring Boot 启动类。由 {@code SpringApplication.run}
 * 引导完成自动装配与组件扫描。
 * </p>
 */
@SpringBootApplication
public class StriptKillGameDemo2Application {

	/**
	 * 启动 Spring Boot 应用。
	 *
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(StriptKillGameDemo2Application.class, args);
	}

}
