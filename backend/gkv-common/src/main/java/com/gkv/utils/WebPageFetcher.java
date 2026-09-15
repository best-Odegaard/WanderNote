package com.gkv.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 抓取外部网页的正文文本（「解析链接生成行程」「App 链接导入行程」用）。
 *
 * 只取首屏 HTML 再去标签，不执行页面里的 JS：
 * 小红书这类分享页会把正文放在 HTML 里供搜索引擎收录，实测能直接取到；
 * 如果站点把正文放到异步接口里（首屏是空壳）或要求登录，这里取不到，
 * 会返回 null，由调用方退回「让用户把正文粘进『行程原文』」的老路径。
 */
@Slf4j
public class WebPageFetcher {

    /** 带浏览器 UA：不少站点对无 UA/爬虫 UA 的请求直接返回验证页或空壳 */
    private static final String UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
            + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    /** 单页最多读 1.5MB，避免被超大页面拖住 Tomcat 线程 */
    private static final int MAX_BYTES = 1_500_000;
    /** 交给 AI 的正文上限：太长的游记反而稀释要点 */
    private static final int MAX_TEXT_CHARS = 4_000;
    /** 少于此长度视为没抓到（空壳页、验证页、纯导航） */
    private static final int MIN_TEXT_CHARS = 60;

    private static final Pattern CHARSET_PATTERN = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_PATTERN = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern OG_TITLE_PATTERN =
            Pattern.compile("(?is)<meta[^>]+property=[\"']og:title[\"'][^>]+content=[\"']([^\"']*)[\"']");
    private static final Pattern OG_TITLE_PATTERN_REV =
            Pattern.compile("(?is)<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']og:title[\"']");

    @Data
    public static class Page {
        /** 页面标题（og:title 优先），可能为空字符串 */
        private String title = "";
        /** 去标签后的正文 */
        private String text = "";
    }

    /**
     * 抓取网页正文。失败、被拦、内容过少都返回 null。
     * 只允许 http/https，且拒绝内网地址（防被当成探测内网的工具）。
     */
    public static Page fetch(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        String target = url.trim();
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            log.info("抓取跳过：非 http(s) 链接 {}", target);
            return null;
        }
        try {
            URL parsed = new URL(target);
            InetAddress addr = InetAddress.getByName(parsed.getHost());
            if (addr.isLoopbackAddress() || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress() || addr.isAnyLocalAddress() || addr.isMulticastAddress()) {
                log.warn("抓取拒绝：目标解析到内网地址 {}", parsed.getHost());
                return null;
            }
        } catch (Exception e) {
            log.info("抓取跳过：链接无法解析 {}（{}）", target, e.getMessage());
            return null;
        }

        HttpURLConnection conn = null;
        try {
            URL parsed = new URL(target);
            conn = (HttpURLConnection) parsed.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", UA);
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                log.info("抓取失败：HTTP {} url={}", status, target);
                return null;
            }
            String html = readBody(conn);
            if (html.isEmpty()) {
                log.info("抓取失败：响应体为空 url={}", target);
                return null;
            }
            String text = htmlToText(html);
            if (text.length() < MIN_TEXT_CHARS) {
                log.info("抓取到的正文过短（{}字），按失败处理 url={}", text.length(), target);
                return null;
            }
            if (text.length() > MAX_TEXT_CHARS) {
                text = text.substring(0, MAX_TEXT_CHARS);
            }
            Page page = new Page();
            page.setTitle(extractTitle(html));
            page.setText(text);
            return page;
        } catch (Exception e) {
            log.info("抓取失败：url={}，{}", target, e.getMessage());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** 按响应头的 charset 解码，最多读 MAX_BYTES */
    private static String readBody(HttpURLConnection conn) throws Exception {
        String contentType = conn.getContentType();
        Charset charset = StandardCharsets.UTF_8;
        if (contentType != null) {
            Matcher m = CHARSET_PATTERN.matcher(contentType);
            if (m.find()) {
                try {
                    charset = Charset.forName(m.group(1));
                } catch (Exception ignored) {
                    // 认不出就用 UTF-8
                }
            }
        }
        try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(chunk)) > 0) {
                total += n;
                if (total > MAX_BYTES) {
                    buf.write(chunk, 0, n - (total - MAX_BYTES));
                    break;
                }
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), charset);
        }
    }

    /** 去脚本、去样式、去标签，保留段落换行 */
    private static String htmlToText(String html) {
        String s = html
                .replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<noscript.*?</noscript>", " ")
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</(p|div|li|h[1-6]|tr)>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        s = unescape(s);
        return s.replaceAll("[ \\t\\x0B\\f\\r\\u00a0]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n")
                .trim();
    }

    private static String extractTitle(String html) {
        Matcher og = OG_TITLE_PATTERN.matcher(html);
        if (og.find()) {
            return unescape(og.group(1)).trim();
        }
        Matcher ogRev = OG_TITLE_PATTERN_REV.matcher(html);
        if (ogRev.find()) {
            return unescape(ogRev.group(1)).trim();
        }
        Matcher title = TITLE_PATTERN.matcher(html);
        return title.find() ? unescape(title.group(1)).replaceAll("\\s+", " ").trim() : "";
    }

    /** 只处理常见的几个实体，够用即可 */
    private static String unescape(String s) {
        return s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
    }
}
