package my.spider.utils;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.util.StringUtils;
import lombok.Data;


// 網址說明
// http://aaa.bbb.ccc/ppp/qqq/index.html#rrr?qqq&ppp
// protocal: http
// host: aaa.bbb.ccc
// path: /ppp/qqq/index.html
// ref: rrr
// query: qqq&ppp

@Data
public class UrlElement
{
	private URL url = null;
	private String urlPath = "";
	private String urlQuery = "";
	private String urlRef = "";
	private Path downloadPath = null;

	public UrlElement()
	{
		// do nothing
	}

	// 確認 root path 全用 '\' 分隔, 且最後沒有 '\'
	public UrlElement(String _url, String rootPath, String defaultPageName)
	{
		if (!StringUtils.hasText(_url)) return;

		try {
			url = new URI(_url).toURL();
			urlPath = url.getProtocol() + "://" + url.getHost() + url.getPath();
			urlQuery = StringUtils.hasText(url.getQuery()) ? "?" + url.getQuery() : "";
			urlRef = StringUtils.hasText(url.getRef()) ? "#" + url.getRef() : "";
			downloadPath = generateDownloadPathFromUrl(url, rootPath, defaultPageName);
		} catch (MalformedURLException | URISyntaxException e) {
			url = null;
			urlPath = "";
			urlQuery = "";
			urlRef = "";
			downloadPath = null;
		}
	}

	public boolean isNullUrl() { return (url == null); }
	public boolean hasQuery() { return (StringUtils.hasText(url.getQuery())); }
	public boolean hasRef() { return (StringUtils.hasText(url.getRef())); }
	public String toString() { return url.toString(); }

	private Path generateDownloadPathFromUrl(URL _url, String rootPath, String defaultPageName)
	{
		// 根據 host 及 level 決定是否要下載
		String host = _url.getHost();

		// 根據 path 決定下載檔案路徑
		String path = _url.getPath();
		// String history = host + path;

		// 假設 path 只有三種情形, 之後遇到再說.
		// 1. 結尾是 / --> 直接加 index.html
		// 2. 結尾是檔案 (有附檔名) --> 不用變
		// 3. 其他 --> 加 .html
		if (path.endsWith("/")) path = path + defaultPageName;
		else {
			String[] pathPart = path.split("/");
			if (pathPart[pathPart.length - 1].indexOf(".") < 0) path += ".html";
		}

		String encodePath = path;
		try{
			encodePath = URLDecoder.decode(path, "UTF-8");
		}catch(Exception ex){
			// do nothing if decode error.
			encodePath = path;
		}

		// generate downloadPath
		Path downloadPath = Paths.get("");
		String dlpath = String.format("%s\\%s%s", rootPath, host, encodePath);
		downloadPath = Paths.get(dlpath);

		return downloadPath;
	}
}
