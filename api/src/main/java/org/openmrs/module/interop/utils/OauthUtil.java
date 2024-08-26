/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.interop.utils;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ObjectNode;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;
import org.openmrs.module.interop.InteropConstant;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OauthUtil {
	
	private static final Pattern pat = Pattern.compile(".*\"access_token\"\\s*:\\s*\"([^\"]+)\".*");
	
	private String strClientId = "";
	
	private String strClientSecret = "";
	
	private String strScope = "";
	
	private String strTokenUrl = "";
	
	private String userName = "";
	
	private String password = "";
	
	public boolean initAuthVars() {
		GlobalProperty globalTokenUrl = Context.getAdministrationService()
		        .getGlobalPropertyObject(InteropConstant.GP_SHR_TOKEN_URL);
		this.strTokenUrl = globalTokenUrl.getPropertyValue();
		GlobalProperty globalScope = Context.getAdministrationService()
		        .getGlobalPropertyObject(InteropConstant.GP_SHR_OAUTH2_SCOPE);
		this.strScope = globalScope.getPropertyValue();
		GlobalProperty globalClientSecret = Context.getAdministrationService()
		        .getGlobalPropertyObject(InteropConstant.GP_SHR_OAUTH2_CLIENT_SECRET);
		this.strClientSecret = globalClientSecret.getPropertyValue();
		GlobalProperty globalClientId = Context.getAdministrationService()
		        .getGlobalPropertyObject(InteropConstant.GP_SHR_OAUTH2_CLIENT_ID);
		this.strClientId = globalClientId.getPropertyValue();
		if (this.strTokenUrl != null && this.strScope != null && this.strClientSecret != null && this.strClientId != null) {
			return true;
		} else {
			System.err.println("Get oauth data: Please set OAuth2 credentials");
			return false;
		}
	}
	
	private String getClientCredentials() {
		String auth = this.strClientId + ":" + this.strClientSecret;
		String authentication = Base64.getEncoder().encodeToString(auth.getBytes());
		BufferedReader reader = null;
		HttpsURLConnection connection = null;
		String returnValue = "";
		
		try {
			StringBuilder parameters = new StringBuilder();
			parameters.append("grant_type=" + URLEncoder.encode("client_credentials", "UTF-8"));
			parameters.append("&");
			parameters.append("scope=" + URLEncoder.encode(this.strScope, "UTF-8"));
			URL url = new URL(this.strTokenUrl);
			connection = (HttpsURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setRequestProperty("Authorization", "Basic " + authentication);
			connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
			connection.setRequestProperty("Accept", "application/json");
			connection.setConnectTimeout(10000);
			PrintStream os = new PrintStream(connection.getOutputStream());
			os.print(parameters);
			os.close();
			reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line = null;
			StringWriter out = new StringWriter(connection.getContentLength() > 0 ? connection.getContentLength() : 2048);
			
			while ((line = reader.readLine()) != null) {
				out.append(line);
			}
			
			String response = out.toString();
			Matcher matcher = pat.matcher(response);
			if (matcher.matches() && matcher.groupCount() > 0) {
				returnValue = matcher.group(1);
			} else {
				System.err.println("OAUTH2 Error : Token pattern mismatch");
			}
		}
		catch (Exception var21) {
			System.err.println("OAUTH2 - Error : " + var21.getMessage());
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (IOException var20) {}
			}
			
			connection.disconnect();
		}
		
		return returnValue;
	}
	
	private boolean isValidToken() {
		String currentToken = Context.getAdministrationService().getGlobalProperty(InteropConstant.GP_SHR_TOKEN);
		ObjectMapper mapper = new ObjectMapper();
		
		try {
			ObjectNode jsonNode = (ObjectNode) mapper.readTree(currentToken);
			if (jsonNode != null) {
				String token = jsonNode.get("access_token").getTextValue();
				if (token != null && token.length() > 0) {
					String[] chunks = token.split("\\.");
					Base64.Decoder decoder = Base64.getUrlDecoder();
					new String(decoder.decode(chunks[0]));
					String payload = new String(decoder.decode(chunks[1]));
					ObjectNode payloadNode = (ObjectNode) mapper.readTree(payload);
					long expiryTime = payloadNode.get("exp").getLongValue();
					long currentTime = System.currentTimeMillis() / 1000L;
					return currentTime < expiryTime;
				} else {
					return false;
				}
			} else {
				return false;
			}
		}
		catch (Exception var16) {
			return false;
		}
	}
	
	public String getToken() {
		if (this.isValidToken()) {
			return Context.getAdministrationService().getGlobalProperty(InteropConstant.GP_SHR_TOKEN);
		} else {
			boolean varsOk = this.initAuthVars();
			if (varsOk) {
				String credentials = this.getClientCredentials();
				if (credentials != null) {
					Context.getAdministrationService().setGlobalProperty(InteropConstant.GP_SHR_TOKEN, credentials);
					return credentials;
				}
			}
			
			return null;
		}
	}
	
	public String getBasicAuthToken() {
		GlobalProperty username = Context.getAdministrationService().getGlobalPropertyObject(InteropConstant.OAUTH_USERNAME);
		
		GlobalProperty password = Context.getAdministrationService().getGlobalPropertyObject(InteropConstant.OAUTH_PASSWORD);
		
		String authString = username + ":" + password;
		String encodedAuthString = Base64.getEncoder().encodeToString(authString.getBytes());
		
		return encodedAuthString;
	}
	
}
