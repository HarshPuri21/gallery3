package com.example.gallerygrabber;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/** Whitelisted URL validation so the background browser never visits arbitrary hosts. */
public final class SiteRules {
    private SiteRules() {}
    private static final Pattern XASIAT_ALBUM = Pattern.compile("^/(?:ja/|fr/)?albums/\\d+/[^/?#]+/?$");
    private static final Pattern V2PH_ALBUM = Pattern.compile("^/album/[^/?#]+\\.html$");

    public static String site(String input) {
        try {
            URI u = URI.create(input.trim());
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getUserInfo() != null ||
                    u.getHost() == null || u.getPort() != -1) return null;
            String host = u.getHost().toLowerCase(Locale.ROOT);
            if ((host.equals("v2ph.com") || host.equals("www.v2ph.com")) &&
                    V2PH_ALBUM.matcher(u.getPath()).matches()) return "V2PH";
            if ((host.equals("xasiat.com") || host.equals("www.xasiat.com")) &&
                    XASIAT_ALBUM.matcher(u.getPath()).matches()) return "Xasiat";
            return null;
        } catch (RuntimeException ex) { return null; }
    }

    public static String albumKey(String url) {
        URI uri = URI.create(url);
        String[] parts = uri.getPath().split("/");
        if ("V2PH".equals(site(url))) return parts[parts.length-1].replace(".html", "");
        // Avoid filename collisions when two Xasiat albums have identical titles.
        for (int n=0; n<parts.length-1; n++) if (parts[n].equals("albums")) return parts[n+1];
        return "album";
    }
}
