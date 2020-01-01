/*******************************************************************************
 * Copyright (c) 2003-2020 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.tmengine;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.mapdb.BTreeMap;
import org.mapdb.DB;
import org.mapdb.DBMaker;

public class TuvDatabase {

	private Map<String, BTreeMap<Integer, String>> textMaps;
	private Map<String, BTreeMap<Integer, Set<String>>> hashesMaps;
	private Map<String, BTreeMap<Integer, String>> segsMaps;
	private Map<String, DB> databases;
	private File folder;

	public TuvDatabase(File folder) {
		this.folder = folder;
		databases = new ConcurrentHashMap<>();
		textMaps = new ConcurrentHashMap<>();
		hashesMaps = new ConcurrentHashMap<>();
		segsMaps = new ConcurrentHashMap<>();
	}

	private void buildIndex(String lang) throws IOException {
		try {
			DB mapdb = DBMaker.newFileDB(new File(folder, "tuv_" + lang)).closeOnJvmShutdown().asyncWriteEnable()
					.make();
			databases.put(lang, mapdb);
			textMaps.put(lang, databases.get(lang).getTreeMap("tuvs"));
			hashesMaps.put(lang, databases.get(lang).getTreeMap("hashes"));
			segsMaps.put(lang, databases.get(lang).getTreeMap("segs"));
		} catch (Error ioe) {
			throw new IOException(ioe.getMessage());
		}
	}

	public void commit() {
		Set<String> langSet = databases.keySet();
		Iterator<String> keys = langSet.iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			databases.get(key).commit();
		}
	}

	public void close() {
		commit();
		Set<String> langSet = databases.keySet();
		Iterator<String> keys = langSet.iterator();
		while (keys.hasNext()) {
			String key = keys.next();
			databases.get(key).close();
		}
	}

	public void store(String lang, String tuid, String puretext, String seg) throws IOException {
		if (!textMaps.containsKey(lang)) {
			buildIndex(lang);
		}
		int idHash = tuid.hashCode();
		BTreeMap<Integer, String> textmap = textMaps.get(lang);
		if (textmap.containsKey(idHash)) {
			textmap.replace(idHash, puretext);
		} else {
			textmap.put(idHash, puretext);
		}
		BTreeMap<Integer, String> segmap = segsMaps.get(lang);
		if (segmap.containsKey(idHash)) {
			segmap.replace(idHash, seg);
		} else {
			segmap.put(idHash, seg);
		}
		int hash = puretext.toLowerCase().hashCode();
		BTreeMap<Integer, Set<String>> hashmap = hashesMaps.get(lang);
		if (hashmap.containsKey(hash)) {
			Set<String> set = hashesMaps.get(lang).get(hash);
			set.add(tuid);
			hashmap.replace(hash, set);
		} else {
			Set<String> set = new TreeSet<>();
			set.add(tuid);
			hashmap.put(hash, set);
		}
	}

	public String getSegText(String lang, String tuid) throws IOException {
		if (!segsMaps.containsKey(lang)) {
			buildIndex(lang);
		}
		return segsMaps.get(lang).get(tuid.hashCode());
	}

	public void remove(String lang, String tuid) throws IOException {
		if (!textMaps.containsKey(lang)) {
			buildIndex(lang);
		}
		int idHash = tuid.hashCode();
		String oldText = getPureText(lang, idHash);
		if (oldText != null) {
			textMaps.get(lang).remove(idHash);
			segsMaps.get(lang).remove(idHash);
			int textHash = oldText.toLowerCase().hashCode();
			Set<String> set = hashesMaps.get(lang).get(textHash);
			set.remove(tuid);
			hashesMaps.get(lang).replace(textHash, set);
		}
	}

	public Set<String> getPerfectMatches(String lang, String searchStr) throws IOException {
		if (!hashesMaps.containsKey(lang)) {
			buildIndex(lang);
		}
		BTreeMap<Integer, Set<String>> hashmap = hashesMaps.get(lang);
		int textHash = searchStr.toLowerCase().hashCode();
		if (hashmap.containsKey(textHash)) {
			return hashmap.get(textHash);
		}
		return new TreeSet<>();
	}

	public NavigableSet<Integer> getKeySet(String lang) throws IOException {
		if (!hashesMaps.containsKey(lang)) {
			buildIndex(lang);
		}
		return textMaps.get(lang).keySet();
	}

	public String getPureText(String lang, Integer id) throws IOException {
		if (!textMaps.containsKey(lang)) {
			buildIndex(lang);
		}
		return textMaps.get(lang).get(id);
	}
}
