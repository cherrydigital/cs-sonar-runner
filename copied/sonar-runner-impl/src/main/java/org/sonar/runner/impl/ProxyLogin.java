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

import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.URLEncoder;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Iterator;
import java.util.Set;
import java.util.HashMap;


public class ProxyLogin {

  private  HttpURLConnection connection = null;

  public ProxyLogin(String url) throws Exception{
    URL siteUrl = new URL(url);
    CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
    connection = (HttpURLConnection) siteUrl.openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setDoInput(true);
  }

  public  void readContents() throws Exception{
    BufferedReader in = null;
    try {
      in = new BufferedReader(
      new InputStreamReader(connection.getInputStream()));
      String inputLine;
      while ((inputLine = in.readLine()) != null) {
        // System.out.println(inputLine);
      }
    }
    finally {
      if(in != null) {
        in.close();
      }
    }
  }

  public void doSubmit(Map<String, String> data) throws Exception {
		DataOutputStream out = null;
    try {
      out = new DataOutputStream(connection.getOutputStream());
      Set keys = data.keySet();
      Iterator keyIter = keys.iterator();
      String content = "";
      for(int i=0; keyIter.hasNext(); i++) {
        Object key = keyIter.next();
        if(i!=0) {
          content += "&";
        }
        content += key + "=" + URLEncoder.encode(data.get(key), "UTF-8");
      }
      //System.out.println(content);
      out.writeBytes(content);
      out.flush();
    }
    finally {
      if(out != null) {
        out.close();
      }
    }
	}
	
	public void login (String id, String password) throws Exception {
    Map<String, String> map = new HashMap<String, String>();
    map.put("username", id);
    map.put("password", password);
		doSubmit(map);
    readContents();
	}

  public void close() {
    if(connection != null) {
      connection.disconnect();
    }
  }
}
