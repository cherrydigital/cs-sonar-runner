/*
 * SonarQube Runner - Distribution
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

/* cherrysis update */

package org.sonar.runner;

import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.sonar.runner.impl.Logs;
import org.sonar.runner.impl.SonarContext;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import java.lang.Process;
import java.lang.Runtime;
import java.lang.Long;
import java.lang.Thread;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * Arguments :
 * <ul>
 * <li>runner.home: optional path to runner home (root directory with sub-directories bin, lib and conf)</li>
 * <li>runner.settings: optional path to runner global settings, usually ${runner.home}/conf/sonar-runner.properties.
 * This property is used only if ${runner.home} is not defined</li>
 * <li>project.home: path to project root directory. If not set, then it's supposed to be the directory where the runner is executed</li>
 * <li>project.settings: optional path to project settings. Default value is ${project.home}/sonar-project.properties.</li>
 * </ul>
 *
 * @since 1.0
 */
public class Main {


  private final Exit exit;
  private final Cli cli;
  private final Conf conf;
  private final RunnerFactory runnerFactory;

  private final static String DEFAULT_BUILD_USERID = "admin";

  // apache http client setup
  private BasicCookieStore httpCookieStore;
  private RequestConfig globalConfig;
  private HttpClientContext httpClientContext;
  private  HttpClientBuilder httpClientBuilder;

  Main(Exit exit, Cli cli, Conf conf, RunnerFactory runnerFactory) {
    this.exit = exit;
    this.cli = cli;
    this.conf = conf;
    this.runnerFactory = runnerFactory;

    // apache http client setup
    this.httpCookieStore = new BasicCookieStore();
    this.globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY).build();
    this.httpClientContext = HttpClientContext.create();
    this.httpClientContext.setCookieStore(httpCookieStore);
    this.httpClientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(globalConfig).setDefaultCookieStore(httpCookieStore);
  }

  void execute() {
    SystemInfo.print();
    if (!cli.isDisplayVersionOnly()) {
      int status = executeTask();
      exit.exit(status);
    }
  }

  private int executeTask() {
    Stats stats = new Stats().start();
    try {
      if (cli.isDisplayStackTrace()) {
        Logs.info("Error stacktraces are turned on.");
      }
      runnerFactory.create(conf.properties()).execute();

    } catch (Exception e) {
      displayExecutionResult(stats, "FAILURE");
      showError("Error during Sonar runner execution", e, cli.isDisplayStackTrace());
      return Exit.ERROR;
    }

    // add permission to buildUserId
    try {
      String buildUserId = System.getProperty("build.user.id");
      Properties props = conf.properties();
      String login = props.getProperty("sonar.login");

      String hostUrl = (String)props.get("sonar.host.url");
      Map<String, String> env = System.getenv();

      String password = SonarContext.getInstance(props).getSonarPassword();
      String project_key = props.getProperty("sonar.projectKey");
      String command;
      Process proc;
      BufferedReader reader;
      String line = "";
      //String tid = (new Long(Thread.currentThread())).toString();
      String tid = String.valueOf(Thread.currentThread().getId());
      String cookie = "cookie-" + Long.toString(new SecureRandom().nextLong());

      // login
      HttpPost httpRequest;
      ArrayList<BasicNameValuePair> postParameters = new ArrayList<BasicNameValuePair>();

      String reqUrl = URIUtils.extractHost(new URI(hostUrl)).toString() + "/dologin" ;
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("username", login));
      postParameters.add(new BasicNameValuePair("password", password));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Login succeed");

      //user permission remove anyone
      reqUrl = hostUrl + "/api/permissions/remove"; //permission=user&group=anyone&component=" + project_key ;
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("permission", "user"));
      postParameters.add(new BasicNameValuePair("group", "anyone"));
      postParameters.add(new BasicNameValuePair("component", project_key));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Removed permission 'user' from group anyone");

      //user permission to sonar-users
      reqUrl = hostUrl + "/api/permissions/add"; //?permission=user&group=sonar-users&component=" + project_key;
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("permission", "user"));
      postParameters.add(new BasicNameValuePair("group", "sonar-users"));
      postParameters.add(new BasicNameValuePair("component", project_key));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Added permission 'user' to group sonar-users");

      //admin permission to sonar-administrators
      reqUrl = hostUrl + "/api/permissions/add"; //?permission=admin&group=sonar-administrators&component=" + project_key + "'";
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("permission", "admin"));
      postParameters.add(new BasicNameValuePair("group", "sonar-administrators"));
      postParameters.add(new BasicNameValuePair("component", project_key));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Added permission 'admin' to group sonar-administrators");

      //admin permission to self
      reqUrl =  hostUrl + "/api/permissions/add"; //?permission=admin&user=" + buildUserId + "&component=" + project_key + "'";
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("permission", "admin"));
      postParameters.add(new BasicNameValuePair("user", buildUserId));
      postParameters.add(new BasicNameValuePair("component", project_key));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Added permission 'admin' to user " +  buildUserId);

      //codeviewer permission to self, maybe unnecessary
      reqUrl = hostUrl + "/api/permissions/add"; //?permission=codeviewer&user=" + buildUserId + "&component=" + project_key;
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("permission", "codeviewer"));
      postParameters.add(new BasicNameValuePair("user", buildUserId));
      postParameters.add(new BasicNameValuePair("component", project_key));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Added permission 'codeviewer' to user " +  buildUserId);

      //remove codeviewer permission from anyone
      reqUrl = hostUrl + "/api/permissions/remove"; //?permission=codeviewer&group=anyone&component=" + project_key + "'";
      httpRequest = new HttpPost(reqUrl);
      postParameters.clear();
      postParameters.add(new BasicNameValuePair("permission", "codeviewer"));
      postParameters.add(new BasicNameValuePair("group", "anyone"));
      postParameters.add(new BasicNameValuePair("component", project_key));
      httpRequest.setEntity(new UrlEncodedFormEntity(postParameters));
      doRequest(httpRequest);
      Logs.info("Removed permission 'codeviewer' from group anyone");

    }
    catch(Exception e) {
      showError("Error during Sonar runner execution", e, cli.isDisplayStackTrace());
      return Exit.ERROR;
    }

    displayExecutionResult(stats, "SUCCESS");
    return Exit.SUCCESS;
  }

  private void displayExecutionResult(Stats stats, String resultMsg) {
    Logs.info("------------------------------------------------------------------------");
    Logs.info("EXECUTION " + resultMsg);
    Logs.info("------------------------------------------------------------------------");
    stats.stop();
    Logs.info("------------------------------------------------------------------------");
  }

  private void doRequest(HttpPost httpRequest) throws Exception{
    CloseableHttpClient httpClient = null;
    InputStream content = null;
    StringWriter writer = null;
    try {
      httpClient = httpClientBuilder.build();
      CloseableHttpResponse response = httpClient.execute(httpRequest);
      int code = response.getStatusLine().getStatusCode();
      writer = new StringWriter();
      content = response.getEntity().getContent();
      if(content != null) {
        IOUtils.copy(response.getEntity().getContent(), writer);
      }

      if( String.valueOf(code).startsWith("20") ) {
        Logs.info("success url:" + httpRequest.getURI().toString());
      }
      else {
        throw new Exception("Http error code:" + String.valueOf(code) + " content:"  + writer.toString());
      }
    }
    finally {
      try {
        if(httpClient != null) {
          httpClient.close();
        }
      }
      catch(Exception e) {}
      try {
        if(content != null) {
          httpClient.close();
        }
      }
      catch(Exception e) {}
      try {
        if(writer != null) {
          httpClient.close();
        }
      }
      catch(Exception e) {}
    }
  }

  private void doLogin() {

  }

  public void showError(String message, Throwable e, boolean showStackTrace) {
    if (showStackTrace) {
      Logs.error(message, e);
      if (!cli.isDebugMode()) {
        Logs.error("");
        suggestDebugMode();
      }
    } else {
      Logs.error(message);
      if (e != null) {
        Logs.error(e.getMessage());
        String previousMsg = "";
        for (Throwable cause = e.getCause(); cause != null
          && cause.getMessage() != null
          && !cause.getMessage().equals(previousMsg); cause = cause.getCause()) {
          Logs.error("Caused by: " + cause.getMessage());
          previousMsg = cause.getMessage();
        }
      }
      Logs.error("");
      Logs.error("To see the full stack trace of the errors, re-run SonarQube Runner with the -e switch.");
      if (!cli.isDebugMode()) {
        suggestDebugMode();
      }
    }
  }

  private void suggestDebugMode() {
    Logs.error("Re-run SonarQube Runner using the -X switch to enable full debug logging.");
  }


  public static void main(String[] args) {
    //Jenkins' user build vars plugin must be set.
    String buildUserId = (String)System.getenv().get("BUILD_USER_ID");
    if(buildUserId == null || buildUserId.length() == 0) {
      buildUserId = DEFAULT_BUILD_USERID;
    }
    System.setProperty("build.user.id", buildUserId);

    Cli cli = new Cli().parse(args);
    Main main = new Main(new Exit(), cli, new Conf(cli), new RunnerFactory());
    main.execute();
  }


}
