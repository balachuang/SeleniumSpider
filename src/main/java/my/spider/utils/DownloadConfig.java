package my.spider.utils;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;


@Data
@Configuration
@ConfigurationProperties(prefix = "download")
public class DownloadConfig
{
	private String[] webStartUrl;
	private String[] limitUrl;
	private String outputLocalPath;
	private String defaultPageName;
	private int limitLevel;
	private boolean convertHyperlink;
	private boolean stopOnFail;
	private Map<String, String> cookies;
}
