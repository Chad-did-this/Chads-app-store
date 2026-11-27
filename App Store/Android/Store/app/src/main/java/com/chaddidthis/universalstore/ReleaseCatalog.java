package com.chaddidthis.universalstore;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class ReleaseCatalog {
    static final String REPO = "Chad-did-this/Chads-app-store";
    static final String WEB = "https://github.com/" + REPO;
    static final String SOURCE = WEB + "/releases/download/ios-source/source.json";
    static final String FEED = "https://chad-did-this.github.io/Chads-app-store/release-feed.json";

    static final class App {
        String name, description, category, platform, packageId, version, url, checksumUrl, sha256;
        long size, versionCode;
    }

    private static HttpURLConnection connect(String address) throws Exception {
        URL url = new URL(address);
        for (int i=0; i<6; i++) {
            if (!url.getProtocol().equals("https")) throw new IOException("HTTPS required");
            String host = url.getHost().toLowerCase(Locale.US);
            if (!(host.equals("github.com") || host.equals("api.github.com") ||
                    host.equals("raw.githubusercontent.com") || host.equals("chad-did-this.github.io") || host.endsWith(".githubusercontent.com") ||
                    host.equals("github-releases.githubusercontent.com"))) throw new IOException("Unexpected download host");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(45000);
            c.setInstanceFollowRedirects(false);
            c.setUseCaches(false);
            c.setRequestProperty("Cache-Control", "no-cache");
            c.setRequestProperty("User-Agent", "ChadsAppStore/1.0");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String next = c.getHeaderField("Location");
                c.disconnect();
                if (next == null) throw new IOException("Missing redirect");
                url = new URL(url,next);
                continue;
            }
            if (code != 200) { c.disconnect(); throw new IOException("GitHub HTTP " + code); }
            return c;
        }
        throw new IOException("Too many redirects");
    }

    private static String text(String address) throws Exception {
        HttpURLConnection c = connect(address);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n=in.read(buffer))!=-1) {
                if (out.size()+n > 4*1024*1024) throw new IOException("Response too large");
                out.write(buffer,0,n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally { c.disconnect(); }
    }

    static List<App> load() throws Exception {
        JSONArray registry;
        JSONArray releases;
        try {
            JSONObject feed = new JSONObject(text(FEED+"?refresh="+System.currentTimeMillis()));
            long generated = feed.getLong("generated");
            if (generated<=0 || System.currentTimeMillis()/1000L-generated>86400L) throw new IOException("Published feed is stale");
            registry=feed.getJSONArray("catalog");
            releases=feed.getJSONArray("releases");
        } catch(Exception feedError) {
            registry=new JSONArray(text("https://raw.githubusercontent.com/"+REPO+"/main/docs/catalog.json?refresh="+System.currentTimeMillis()));
            releases=new JSONArray(text("https://api.github.com/repos/"+REPO+"/releases?per_page=100"));
        }
        List<App> apps = new ArrayList<>();
        for (int i=0;i<registry.length();i++) {
            JSONObject item = registry.getJSONObject(i);
            App app = new App();
            app.name = item.getString("name");
            app.description = item.getString("description");
            app.category = item.getString("category");
            app.platform = item.getString("platform");
            app.packageId = item.optString("packageId");
            String prefix = item.getString("releasePrefix");
            String filename = item.getString("asset");
            for (int j=0;j<releases.length();j++) {
                JSONObject release = releases.getJSONObject(j);
                String tag = release.getString("tag_name");
                if (release.optBoolean("draft") || !tag.startsWith(prefix)) continue;
                String version = tag.substring(prefix.length());
                String last = version.substring(version.lastIndexOf('.')+1);
                long code;
                try { code = Long.parseLong(last); } catch (NumberFormatException ex) { continue; }
                if (code <= app.versionCode) continue;
                JSONArray assets = release.getJSONArray("assets");
                JSONObject apk = null;
                String checksum = null;
                for (int k=0;k<assets.length();k++) {
                    JSONObject a = assets.getJSONObject(k);
                    if (filename.equals(a.getString("name"))) apk=a;
                    if ((filename+".sha256").equals(a.getString("name"))) checksum=a.getString("browser_download_url");
                }
                if (apk==null) continue;
                String url=apk.getString("browser_download_url");
                if (!url.startsWith(WEB+"/releases/download/")) continue;
                app.version=version;
                app.versionCode=code;
                app.url=url;
                app.size=apk.getLong("size");
                app.checksumUrl=checksum;
                String digest=apk.optString("digest","");
                app.sha256=digest.startsWith("sha256:")?digest.substring(7):null;
            }
            apps.add(app);
        }
        return apps;
    }

    static File download(App app, File directory) throws Exception {
        String expected=app.sha256;
        if ((expected==null || !expected.matches("[a-fA-F0-9]{64}")) && app.checksumUrl!=null) {
            if (!app.checksumUrl.startsWith(WEB+"/releases/download/")) throw new IOException("Invalid checksum URL");
            expected=text(app.checksumUrl).trim().split("\\s+")[0];
        }
        if (expected==null || !expected.matches("[a-fA-F0-9]{64}")) throw new IOException("No verified APK checksum available");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create download directory");
        if (app.size<=0 || app.size>150L*1024*1024) throw new IOException("Unsupported APK size");
        File file=File.createTempFile("app-", ".apk", directory);
        boolean ok=false;
        HttpURLConnection c=connect(app.url);
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");
            long count=0;
            try (InputStream in=c.getInputStream(); FileOutputStream out=new FileOutputStream(file)) {
                byte[] buffer=new byte[65536];
                int n;
                while ((n=in.read(buffer))!=-1) {
                    count+=n;
                    if (count>app.size) throw new IOException("Size mismatch");
                    md.update(buffer,0,n);
                    out.write(buffer,0,n);
                }
            }
            StringBuilder hex=new StringBuilder();
            for(byte b:md.digest()) hex.append(String.format(Locale.US,"%02x",b&255));
            if(count!=app.size || !expected.equalsIgnoreCase(hex.toString())) throw new IOException("APK verification failed");
            ok=true;
            return file;
        } finally { c.disconnect(); if(!ok) file.delete(); }
    }
}
