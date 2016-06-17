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

import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchResult;
import java.util.Properties;

public class SonarContext {
  private static SonarContext ldapContext = null;
  private String securityKey = "";
  private String login = "";
  private LdapHandler ldapHandler = null;
  public static SonarContext getInstance(Properties props) throws Exception {
    if(ldapContext == null) {
      ldapContext = new SonarContext(props);
    }
    return ldapContext;
  }

  private SonarContext(Properties props) throws Exception {
    String hostUrl = (String)props.get("sonar.host.url");
    String initialContextFactory = props.getProperty("sonar.ldap.initial.context.factory");
    if(initialContextFactory == null) {
      throw new Exception("sonar.ldap.initial.context.factory is null");
    }
    String  providerUrl= props.getProperty("sonar.ldap.provider.url");
    if(providerUrl == null) {
      throw new Exception("sonar.ldap.provider.url is null");
    }
    String  securityAuthentication = props.getProperty("sonar.ldap.security.authetication");
    if(securityAuthentication == null) {
      throw new Exception("sonar.ldap.security.authetication is null");
    }
    String  securityPrincipal = props.getProperty("sonar.ldap.security.principal");
    if(securityPrincipal == null) {
      throw new Exception("sonar.ldap.security.principal is null");
    }
    String  securityCredentials = props.getProperty("sonar.ldap.security.credentials");
    if(securityCredentials == null) {
      throw new Exception("sonar.ldap.security.credentials is null");
    }
    String baseDn = props.getProperty("sonar.ldap.base.dn");
    if(baseDn == null) {
      throw new Exception("sonar.ldap.base.dn is null");
    }

    securityKey = props.getProperty("sonar.security.key");
    if(securityKey == null) {
      throw new Exception("sonar.security.key is null");
    }

    login = props.getProperty("sonar.login");
    if(login == null) {
      throw new Exception("sonar.login is null");
    }
    Logs.info("LdapHandler binding to providerUrl:" + providerUrl + " securityAuthentication:" + securityAuthentication + " securityPrincipal:" + securityPrincipal + " baseDn:" + baseDn);
    ldapHandler = new LdapHandler(initialContextFactory, providerUrl, securityAuthentication, securityPrincipal, securityCredentials, baseDn, securityKey);
    Logs.info("LdapHandler created");
  }

  public String getSonarPassword() throws Exception{
    String password = "";
    String filter = "uid=" + login;
    NamingEnumeration<SearchResult> results = ldapHandler.search(ldapHandler.MembersEntry, filter);

    if(results != null && results.hasMore()) {
      SearchResult result = results.next();
      String csPassword = (String)result.getAttributes().get("csPassword").get();
      SaltedAesCryptor aesCryptor = new SaltedAesCryptor(securityKey);
      password = aesCryptor.decrpyt(csPassword);
    }
    else {
      Logs.error("no ldap entity found by entry:" + ldapHandler.MembersEntry + " filter:" + filter);
    }
    return password;
  }
}
