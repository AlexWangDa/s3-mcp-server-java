package org.springframework.ai.mcp.sample.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.sample.server.service.S3Service;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SpringBootApplication
public class McpServerApplication {
	public  static String endpoint;
	public static String ak;
	public static String sk;
	private static final Logger log = LoggerFactory.getLogger(McpServerApplication.class);

	public static void main(String[] args) {
		for (String s:args){
			if (s.startsWith("--s3.endpoint")){
				endpoint=s.split("=")[1];
			}
			if (s.startsWith("--s3.accessKey")){
				ak=s.split("=")[1];
			}
			if (s.startsWith("--s3.secretKey")){
				sk=s.split("=")[1];
			}
		}
		SpringApplication.run(McpServerApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider s3Tools(S3Service s3Service) {
		return MethodToolCallbackProvider.builder().toolObjects(s3Service).build();
	}

}
