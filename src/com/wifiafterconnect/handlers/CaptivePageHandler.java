/*
 * Copyright (C) 2013 Sasha Vasko <sasha at aftercode dot net> 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wifiafterconnect.handlers;

import java.net.URL;
import java.util.HashMap;

import com.wifiafterconnect.Constants;
import com.wifiafterconnect.WifiAuthParams;

import android.util.Log;

import com.wifiafterconnect.html.HtmlForm;
import com.wifiafterconnect.html.HtmlPage;

public abstract class CaptivePageHandler {

	public interface Detection {
		public Boolean detect(HtmlPage page); 
	}
	
	// Strictly speaking we don't really need a map here for now
	// May add methods for listing available handlers in future
	private static HashMap <String,Class<? extends Detection>> registeredHandlers = null;
	
	// Using Reflection and package listing is way too much trouble
	private static final String[] standardHandlers = new String[] {
		"CiscoHandler",
		"UniFiHandler",
		"WanderingWifiHandler",
		"WiNGHandler"
	};

	@SuppressWarnings("unchecked")
	private static void registerStandardHandlers() {
		if (registeredHandlers != null)
			return;
		
		registeredHandlers = new HashMap<String,Class<? extends Detection>>();
		for (String handlerName : standardHandlers) {
			try {
				registerHandler ((Class<? extends Detection>)Class.forName(CaptivePageHandler.class.getPackage().getName() + '.' + handlerName));
			} catch (ClassNotFoundException e) {// don't care
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Register new Captive Portal handler at runtime.
	 * 
	 * @param handler - class of the handler to be registered. 
	 * Must extend CaptivePageHandler and implement CaptivePageHandler.Detection
	 */
	public static void registerHandler (Class<? extends Detection> handler) {
		if (registeredHandlers == null)
			registerStandardHandlers();
			
		registeredHandlers.put (handler.getName(), handler);
	}
	
	// THE FACTORY
	public static CaptivePageHandler autodetect (HtmlPage page) {
		
		if (page == null || page.getForm() == null)
			return null;
		
		if (registeredHandlers == null)
			registerStandardHandlers();
		
		for (Class<? extends Detection> handlerClass : registeredHandlers.values()) {
			try {
				CaptivePageHandler handler = (CaptivePageHandler) handlerClass.newInstance();
				//Method m = handlerClass.getMethod("detect", HtmlPage.class);
				//Boolean result = (Boolean)m.invoke(handler, page);
				Detection d = (Detection)handler;
				Boolean result = d.detect(page);
				Log.d(Constants.TAG, "detecting " + handlerClass.getName() + " result = " + result);
				if (result) {
					handler.setPage(page);
					return handler;
				}
//			} catch (NoSuchMethodException e) { e.printStackTrace();
			} catch (InstantiationException e) {
				e.printStackTrace();
				return null;
			} catch (IllegalAccessException e) {
				e.printStackTrace();
				return null;
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
//			} catch (InvocationTargetException e) {		e.printStackTrace();
			} catch (NullPointerException e) { // ignore
			}
		}
		return null;
	}
	
	
	protected HtmlPage page = null;
	
	public void setPage (HtmlPage page) {
		this.page = page;
	}
	
	public abstract boolean checkParamsMissing (WifiAuthParams params);
	public abstract void validateLoginForm (WifiAuthParams params, HtmlForm form);
	
	public String getPostData (WifiAuthParams params) {
		HtmlForm form = getLoginForm();
		if (form != null) {
			form.fillInputs(params);
			validateLoginForm (params, form);
			return form.formatPostData();
		}
		return null;
	}

	public HtmlForm getLoginForm () {
		return page != null ? page.getForm() : null;
	}
	
	/* 
	 * Possibly to be overriden in subclasses
	 */
	public URL getPostUrl () 
	{ 
		HtmlForm form = getLoginForm();
		if (form != null)
			return form.formatActionURL (page.getUrl());
		return page.getUrl(); 
	};
	
	public boolean checkUsernamePasswordMissing (WifiAuthParams params){
		HtmlForm form = getLoginForm();
		Log.d(Constants.TAG, "Checking for missing params. Form = " + form);
		return (form != null && form.isParamMissing(params, WifiAuthParams.USERNAME)||form.isParamMissing(params, WifiAuthParams.PASSWORD)); 
	}
	
	public WifiAuthParams addMissingParams (WifiAuthParams params) {
		HtmlForm form = getLoginForm();
		if (form != null) 
			params = form.fillParams (params);
		return params;
	}
	
}