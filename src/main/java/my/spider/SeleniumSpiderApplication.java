package my.spider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class SeleniumSpiderApplication
{
	public static void main(String[] args)
	{
		// Springboots 不啟動 Web Server 的方式: new SpringApplicationBuilder(app.class).web(WebApplicationType.NONE).run(args);
		// Springboots 啟動後直接執行某個程式的方式:
		// 1. 用 ConfigurableApplicationContext 直接呼叫某個 function.
		//    ref: https://ithelp.ithome.com.tw/m/articles/10269415
		// 2. (用這個) 繼承 ApplicationRunner 並實作 run() 讓 Sprinboots 自動執行.
		//    ref: https://blog.csdn.net/goodjava2007/article/details/126356485

		SpringApplication.run(SeleniumSpiderApplication.class, args);
	}
}
