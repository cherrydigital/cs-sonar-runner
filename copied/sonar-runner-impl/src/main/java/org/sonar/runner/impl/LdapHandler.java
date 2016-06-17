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

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.Hashtable;

public class LdapHandler {

  public String MembersEntry;
  private Hashtable<String, String> adminEnv = new Hashtable<String, String>();
  private SearchControls controls;

  public LdapHandler(
    String initialContextFactory,
    String providerUrl,
    String securityAuthentication,
    String securityPrincipal,
    String securityCredentials,
    String baseDn,
    String securityKey
  ) throws Exception{

    SaltedAesCryptor cryptor = new SaltedAesCryptor(securityKey);
    String dCredentials = cryptor.decrpyt(securityCredentials);

    adminEnv.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
    adminEnv.put(Context.PROVIDER_URL, providerUrl);
    adminEnv.put(Context.SECURITY_AUTHENTICATION, securityAuthentication);
    adminEnv.put(Context.SECURITY_PRINCIPAL, securityPrincipal);
    adminEnv.put(Context.SECURITY_CREDENTIALS, dCredentials);

    controls = new SearchControls();
    controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    MembersEntry = "cn=members," + baseDn;
  }

  public NamingEnumeration<SearchResult> search(String entry, String filter ) throws Exception{
    NamingEnumeration<SearchResult> result = null;
    LdapContext ctx = null;
    try {
      ctx = new InitialLdapContext(adminEnv, null);
      result = ctx.search(entry, filter, controls);
    }
    finally {
      if(ctx != null) {
        try{ctx.close();} catch(Exception e){}
      }
    }
    return result;
  }
}