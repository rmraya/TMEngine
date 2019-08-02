/*******************************************************************************
 * Copyright (c) 2003 - 2019 Maxprograms.
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
import java.util.Map;
import java.util.Set;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

public class TuDatabase {

	private DB mapdb;
	private HTreeMap<Integer, Map<String, String>> tumap;
	private Set<String> projects;
	private Set<String> subjects;
	private Set<String> customers;
	private Set<String> languages;

	public TuDatabase(File folder) throws IOException {
		try {
			mapdb = DBMaker.newFileDB(new File(folder, "tudata")).closeOnJvmShutdown().asyncWriteEnable().make();
			tumap = mapdb.getHashMap("tuvmap");
			projects = mapdb.getHashSet("projects");
			subjects = mapdb.getHashSet("subjects");
			customers = mapdb.getHashSet("customers");
			languages = mapdb.getHashSet("languages");
		} catch (Error ioe) {
			throw new IOException(ioe.getMessage());
		}
	}

	public void commit() {
		mapdb.commit();
	}

	public void close() {
		mapdb.commit();
		mapdb.close();
	}

	public void store(String tuid, Map<String, String> tu) {
		if (tumap.containsKey(tuid.hashCode())) {
			tumap.replace(tuid.hashCode(), tu);
		} else {
			tumap.put(tuid.hashCode(), tu);
		}
	}

	public void storeSubject(String sub) {
		subjects.add(sub);
	}

	public void storeCustomer(String cust) {
		customers.add(cust);
	}

	public void storeProject(String proj) {
		projects.add(proj);
	}

	public void storeLanguage(String lang) {
		languages.add(lang);
	}

	public Set<String> getCustomers() {
		return customers;
	}

	public Set<String> getProjects() {
		return projects;
	}

	public Set<String> getSubjects() {
		return subjects;
	}

	public Set<Integer> getKeys() {
		return tumap.keySet();
	}

	public Set<String> getLanguages() {
		return languages;
	}

	public Map<String, String> getTu(Integer hashCode) {
		return tumap.get(hashCode);
	}

	public void remove(String tuid) {
		if (tumap.containsKey(tuid.hashCode())) {
			tumap.remove(tuid.hashCode());
		}
	}
}
