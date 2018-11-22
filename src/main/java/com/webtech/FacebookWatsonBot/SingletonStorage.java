package com.webtech.FacebookWatsonBot;

import java.util.HashMap;
import java.util.Map;

import javax.inject.Singleton;

@Singleton
public class SingletonStorage {
	private static SingletonStorage _instance = null;
	private Map<String, String> watsonSessionIds;
	
	private SingletonStorage() {
		watsonSessionIds = new HashMap<String, String>();
	}
	
	public static SingletonStorage getInstance() {
		if (_instance == null)
			_instance = new SingletonStorage();
		
		return _instance;
	}

	public Map<String, String> getWatsonSessionIds() {
		return watsonSessionIds;
	}
}