package com.gamepaper.claude;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 주어진 URL의 HTML을 가져오는 컴포넌트.
 * 테스트에서 MockBean으로 교체 가능하도록 별도 클래스로 분리.
 */
@Slf4j
@Component
public class HtmlFetcher {

    /**
     * URL에서 HTML을 가져옵니다.
     *
     * @param url 대상 URL
     * @return 페이지 HTML 문자열
     * @throws IOException 페이지 접근 실패 시
     */
    public String fetch(String url) throws IOException {
        log.debug("HTML 수집 시작 - url={}", url);
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; GamePaperBot)")
                .timeout(10_000)
                .get()
                .html();
    }
}
