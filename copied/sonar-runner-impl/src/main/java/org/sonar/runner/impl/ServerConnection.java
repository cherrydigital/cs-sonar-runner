/*
 * SonarQube Runner - Implementation
 * Copyright (C) 2011 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.runner.impl;

import com.github.kevinsawicki.http.HttpRequest;
import org.apache.commons.io.FileUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;

public class ServerConnection {

  static final int CONNECT_TIMEOUT_MILLISECONDS = 30000;
  static final int READ_TIMEOUT_MILLISECONDS = 60000;
  private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");

  private final String serverUrl;
  private final String userAgent;

  private ServerConnection(String serverUrl, String app, String appVersion) {
    this.serverUrl = removeEndSlash(serverUrl);
    this.userAgent = app + "/" + appVersion;
  }

  private String removeEndSlash(String url) {
    if (url == null) {
      return null;
    }
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }

  public static ServerConnection create(Properties properties) {
    String buildUserId = System.getProperty("build.user.id");
    String serverUrl = properties.getProperty("sonar.host.url");
    String app = properties.getProperty(InternalProperties.RUNNER_APP);
    String appVersion = properties.getProperty(InternalProperties.RUNNER_APP_VERSION);

    try {
      // parse server host
      String loginUrl = URIUtils.extractHost(new URI(serverUrl)).toString() + "/dologin";

      // get login info
      String username = properties.getProperty("sonar.login");

      // login to proxy server
      Logs.info("Attempt login to proxy server url:" + loginUrl + " with id:" + username + " on behalf of id:" + buildUserId);

      String password = SonarContext.getInstance(properties).getSonarPassword();

      ProxyLogin proxyLogin = new ProxyLogin(loginUrl);
      proxyLogin.login(username, password);
      proxyLogin.close();
      Logs.info("Login succeed to proxy server:" + loginUrl + " with id:" + username + " on behalf of id:" + buildUserId);
    }
    catch(Exception e) {
      e.printStackTrace();
      return null;
    }

    return new ServerConnection(serverUrl, app, appVersion);
  }

  void download(String path, File toFile) {
    String fullUrl = serverUrl + path;
    try {
      Logs.debug("Download " + fullUrl + " to " + toFile.getAbsolutePath());
      HttpRequest httpRequest = newHttpRequest(new URL(fullUrl));
      if (!httpRequest.ok()) {
        throw new IOException("Status returned by url : '" + fullUrl + "' is invalid : " + httpRequest.code());
      }
      httpRequest.receive(toFile);

    } catch (Exception e) {
      if (e.getCause() instanceof ConnectException || e.getCause() instanceof UnknownHostException) {
        Logs.error("Sonar server '" + serverUrl + "' can not be reached");
      }
      FileUtils.deleteQuietly(toFile);
      throw new IllegalStateException("Fail to download: " + fullUrl, e);

    }
  }

  String downloadString(String path) throws IOException {
    String fullUrl = serverUrl + path;
    HttpRequest httpRequest = newHttpRequest(new URL(fullUrl));
    try {
      String charset = getCharsetFromContentType(httpRequest.contentType());
      if (charset == null || "".equals(charset)) {
        charset = "UTF-8";
      }
      if (!httpRequest.ok()) {
        throw new IOException("Status returned by url : '" + fullUrl + "' is invalid : " + httpRequest.code());
      }
      return httpRequest.body(charset);

    } catch (HttpRequest.HttpRequestException e) {
      if (e.getCause() instanceof ConnectException || e.getCause() instanceof UnknownHostException) {
        Logs.error("Sonar server '" + serverUrl + "' can not be reached");
      }
      throw e;

    } finally {
      httpRequest.disconnect();
    }
  }

  //private HttpRequest newHttpRequest(URL url) {
  public HttpRequest newHttpRequest(URL url) {
    HttpRequest request = HttpRequest.get(url);
    request.trustAllCerts().trustAllHosts();
    request.acceptGzipEncoding().uncompress(true);
    request.connectTimeout(CONNECT_TIMEOUT_MILLISECONDS).readTimeout(READ_TIMEOUT_MILLISECONDS);
    request.userAgent(userAgent);
    return request;
  }

  /**
   * Parse out a charset from a content type header.
   *
   * @param contentType e.g. "text/html; charset=EUC-JP"
   * @return "EUC-JP", or null if not found. Charset is trimmed and upper-cased.
   */
  static String getCharsetFromContentType(String contentType) {
    if (contentType == null) {
      return null;
    }
    Matcher m = CHARSET_PATTERN.matcher(contentType);
    if (m.find()) {
      return m.group(1).trim().toUpperCase();
    }
    return null;
  }
}
