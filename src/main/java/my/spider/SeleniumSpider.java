package my.spider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import my.spider.utils.BrowserConfig;
import my.spider.utils.DownloadConfig;
import my.spider.utils.UrlElement;


@Slf4j
@Component
@Order(1)
public class SeleniumSpider implements CommandLineRunner
{
	@Autowired
	BrowserConfig appConfig;

	@Autowired
	DownloadConfig downloadConfig;

	private ArrayList<Pair<UrlElement, Integer>> waitForDownload = new ArrayList<Pair<UrlElement, Integer>>();
	private HashSet<String> waitForDownloadUrls = new HashSet<String>(); // 用來快速找出某個 url 是否已經在下載佇列中
	private HashSet<String> alreadyDownload = new HashSet<String>();
	private Map<String, String> pageTags = null;
	private Map<String, String> fileTags = null;

	private WebDriver driver = null;
	private String downloadRootPath = "";

	public void run(String... args)
	{
		pageTags = Map.of("a", "href", "iframe", "src");
		fileTags = Map.of("img", "src", "script", "src", "link", "href");

		// Initialize WebDriver
		// Selenium 會自動把所有網址正規化, 而且有找到如何用 js 精確修改 url, 所以不必用 Jsoup 了.
		driver = new ChromeDriver();
		downloadRootPath = getRootPath();

		// put start-pages into download queue
		for (String webUrl : downloadConfig.getWebStartUrl())
		{
			UrlElement url = new UrlElement(webUrl, downloadRootPath, downloadConfig.getDefaultPageName());
			if (url.isNullUrl()) continue;
			if (!isMatchUrLimitation(url.getUrlPath(), 1)) continue;

			waitForDownload.add(Pair.of(url, 1));
			waitForDownloadUrls.add(url.getUrlPath());
		}

		// recursive download all pages
		while(waitForDownload.size() > 0)
		{
			Pair<UrlElement, Integer> next = waitForDownload.remove(0);
			waitForDownloadUrls.remove(next.getFirst().getUrlPath());
			alreadyDownload.add(next.getFirst().getUrlPath());
			if (!downloadPage(next.getFirst(), next.getSecond())) logger.error("ERROR when download: {}", next.getFirst().toString());
		}

		// close WebDriver then End
		driver.quit();
		logger.info("All Download Done.");
	}

	// 功能: 下載網頁, 專門處理內含連結的網頁檔. 網址如果沒有指定附檔名, 則下載為 html 檔 (預設).
	// 回傳: 下載完成後的完整檔名, 
	//       回傳 NULL - 表示下載失敗
	//       回傳空字串 - 表示取消下載, 不需進行後續 url convert
	//
	// 為了要做 url convert, 發現網址時就要先產生對應的完整下載路徑.
	// 因為已經預先產生了, 所以在這裡就直接傳進來.
	// 即然在呼叫就知道路徑了, 也就不用回傳下載路徑了, 直接回傳是否成功就好.
	// 不需要檢查是否下載過. 發現 url 時就要檢查了. 會進來的就是還沒下載過的.
	//
	private boolean downloadPage(UrlElement url, int level)
	{
		// 開啟 url 並取得原始碼 ==> page only
		logger.info("Navigate to: {}", url.toString());
		// driver.get(url.toString());
		navigateTo(url.toString());

		// URL urlObj = null;
		List<WebElement> tagElems = null;
		Iterator<String> tagIterator = null;

		// 取得所有連外的 page url
		tagIterator = pageTags.keySet().iterator();
		while(tagIterator.hasNext())
		{
			String tag = tagIterator.next();
			String urlAtt = pageTags.get(tag);
			tagElems = driver.findElements(By.tagName(tag));
			logger.debug("  Iterate outer link: {}.{} x {}", tag, urlAtt, tagElems.size());
			for (WebElement tagElem : tagElems)
			{
				UrlElement pageUrl = new UrlElement(tagElem.getAttribute(urlAtt), downloadRootPath, downloadConfig.getDefaultPageName());
				if (pageUrl.isNullUrl()) continue;

				// 如果已經下載過或已經預備要下載, 就直接做網址取代, 不用再檢查是否需要下載
				if (alreadyDownload.contains(pageUrl.getUrlPath()) || waitForDownloadUrls.contains(pageUrl.getUrlPath()))
				{
					// 用 javascript 精確取代網址
					if (downloadConfig.isConvertHyperlink())
					{
						String convertTo = url.getDownloadPath().getParent().relativize(pageUrl.getDownloadPath()).toString();
						String convertToUrlAtt = pageUrl.toString().replace(pageUrl.getUrlPath(), convertTo).replaceAll("\\\\", "/");
						((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute('" + urlAtt + "', '" + convertToUrlAtt + "');", tagElem);
					}
				}
				else if(isMatchUrLimitation(pageUrl.getUrlPath(), level))
				{
					// 下載網頁會把 Selenium 內容蓋掉, 所以不能 recurrsive 下載
					waitForDownload.add(Pair.of(pageUrl, level+1));
					waitForDownloadUrls.add(pageUrl.getUrlPath());
					logger.debug("    Add url to download queue: {}", pageUrl.getUrlPath());
				}
			}
		}

		// 取得所有連外的 file url
		tagIterator = fileTags.keySet().iterator();
		while(tagIterator.hasNext())
		{
			String tag = tagIterator.next();
			String urlAtt = fileTags.get(tag);
			tagElems = driver.findElements(By.tagName(tag));
			logger.debug("  Iterate resource: {}.{} x {}", tag, urlAtt, tagElems.size());
			for (WebElement tagElem : tagElems)
			{
				UrlElement fileUrl = new UrlElement(tagElem.getAttribute(urlAtt), downloadRootPath, downloadConfig.getDefaultPageName());
				if (fileUrl.isNullUrl()) continue;

				// 如果已經下載過, 就直接做網址取代, 不用再檢查是否需要下載
				if (alreadyDownload.contains(fileUrl.getUrlPath()))
				{
					// 用 javascript 精確取代網址
					if (downloadConfig.isConvertHyperlink())
					{
						String convertTo = url.getDownloadPath().getParent().relativize(fileUrl.getDownloadPath()).toString();
						String convertToUrlAtt = fileUrl.toString().replace(fileUrl.getUrlPath(), convertTo).replaceAll("\\\\", "/");
						((JavascriptExecutor) driver).executeScript("arguments[0].setAttribute('" + urlAtt + "', '" + convertToUrlAtt + "');", tagElem);
					}
				}
				else if(isMatchUrLimitation(fileUrl.getUrlPath(), level))
				{
					// 下載檔案不需借助 Selenium, 不會有網頁元件被蓋掉的問題, 所以遇到可以直接下載
					downloadFile(fileUrl);
					alreadyDownload.add(fileUrl.getUrlPath());
				}
			}
		}

		// store url to file
		try{
			Files.createDirectories(url.getDownloadPath().getParent());
			Files.writeString(url.getDownloadPath(), driver.getPageSource(), StandardCharsets.UTF_8);
		}catch(Exception ex){
			logger.error("Store file fail: {}", ex.getMessage());
			if (downloadConfig.isStopOnFail()) return false;
		}

		logger.info("  Page download to: {}", url.getDownloadPath());
		return true;
	}

	private boolean downloadFile(UrlElement url)
	{
		// is not a page, just download.
		try{
			InputStream in = url.getUrl().openStream();
			Files.createDirectories(url.getDownloadPath().getParent());
			Files.copy(in, url.getDownloadPath(), StandardCopyOption.REPLACE_EXISTING);
		}catch(Exception ex){
			logger.error("Download File Exception: {}", ex.getMessage());
			return false; // return Emtoy and keep next download
		}

		logger.info("  Resource download to: {}", url.getDownloadPath());
		return true;
	}

	// 確認 root path 全用 '\' 分隔, 且最後沒有 '\'
	private String getRootPath()
	{
		String rp = downloadConfig.getOutputLocalPath().trim().replaceAll("/", "\\");
		rp = StringUtils.trimTrailingCharacter(rp, '\\');
		return rp;
	}

	private void navigateTo(String url)
	{
		Map<String, String> cookies = downloadConfig.getCookies();

		driver.get(url);
		if (cookies.size() > 0)
		{
			Iterator<String> itor = cookies.keySet().iterator();
			while(itor.hasNext())
			{
				String cookieName = itor.next();
				String cookieValue = cookies.get(cookieName);
				driver.manage().addCookie(new Cookie(cookieName, cookieValue));
			}
		}
	}

	private boolean isMatchUrLimitation(String urlStr, int level)
	{
		boolean urlMatchLimit = false;
		boolean urlMatchLevel = false;

		// check url limitation, shoud start with http[s]://
		for (String urlLimit : downloadConfig.getLimitUrl())
		{
			if (urlStr.startsWith(urlLimit))
			{
				urlMatchLimit = true;
				break;
			}
		}

		urlMatchLevel = (downloadConfig.getLimitLevel() <= 0) || (level < downloadConfig.getLimitLevel());

		return urlMatchLimit && urlMatchLevel;
	}
}
