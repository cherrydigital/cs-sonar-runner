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


import org.apache.commons.codec.binary.Base64;

import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.spec.AlgorithmParameterSpec;
import java.util.UUID;

//import com.jinvis.cherry.common.SaltedAesCryptor;
//import com.jinvis.cherry.common.SaltedAesCryptorFactory;

public class SaltedAesCryptor{
  private int keySize = 16;
  byte[] keyBytes = new byte[keySize];
  private String algorithm = "AES/CBC/PKCS5Padding";


  private SecretKeySpec secretKeySpec;
  private AlgorithmParameterSpec algorithmParameterSpec;

  public SaltedAesCryptor(String encKey) throws Exception{
    MessageDigest digest = MessageDigest.getInstance("MD5");
    digest.update(encKey.getBytes("UTF-8"));
    System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);

    this.secretKeySpec = new SecretKeySpec(keyBytes, "AES");
    byte[] iv = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    algorithmParameterSpec = new IvParameterSpec(iv);
  }

  public String encrypt(String plainText) throws Exception{
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, algorithmParameterSpec);
    String salt = UUID.randomUUID().toString().substring(0,8);
    byte[] bytes = (salt + plainText).getBytes("UTF-8");
    byte[] encrypted = cipher.doFinal(bytes);
    return new String(Base64.encodeBase64(encrypted));
  }


  public String  decrpyt(String encText) throws Exception{
    Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, algorithmParameterSpec);
    byte[] bytes = Base64.decodeBase64(encText);
    byte[] nBytes;

    String decText = "";
    String secretText = "";
    String parsedText = "";

    try {
      nBytes = cipher.doFinal(bytes);
      secretText = new String(nBytes, "UTF-8");
      try {
        parsedText = secretText.substring(8);
        decText = parsedText;
      }
      catch(StringIndexOutOfBoundsException e) {
        decText = secretText;
      }
      catch(Exception e) {
        e.addSuppressed(new Exception("Error")); throw e;
      }
    }
    catch(IllegalBlockSizeException e) {
      Logs.info("Not encrypted security phrase");
      decText = encText;
    }



    return decText;
  }
}
