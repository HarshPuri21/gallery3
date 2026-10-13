package com.example.gallerygrabber;

import org.junit.Test;
import static org.junit.Assert.*;

public class SiteRulesTest {
    @Test public void supportsAlbumUrls() {
        assertEquals("V2PH", SiteRules.site("https://www.v2ph.com/album/z7xn5x4a.html?page=2"));
        assertEquals("Xasiat", SiteRules.site("https://www.xasiat.com/albums/28156/photobook/"));
        assertEquals("Xasiat", SiteRules.site("https://www.xasiat.com/ja/albums/28155/album-title/"));
        assertEquals("28155", SiteRules.albumKey("https://www.xasiat.com/ja/albums/28155/album-title/"));
    }
    @Test public void rejectsOtherHostsAndNonAlbums() {
        assertNull(SiteRules.site("https://v2ph.com.evil.test/album/x.html"));
        assertNull(SiteRules.site("file:///etc/passwd"));
        assertNull(SiteRules.site("https://www.xasiat.com/members/1/albums/"));
        assertNull(SiteRules.site("http://www.xasiat.com/albums/123/a/"));
    }
}
