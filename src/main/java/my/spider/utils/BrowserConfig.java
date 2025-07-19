package my.spider.utils;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;


@Data
@Configuration
@ConfigurationProperties(prefix = "browser")
public class BrowserConfig
{
	private String browserDriver;
	private long globalTimeout;
	private long scriptTimeout;
	private long pageTimeout;
	private Map<String, String> cookies;
}
