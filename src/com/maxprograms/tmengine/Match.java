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

import java.io.Serializable;
import java.util.Map;

import com.maxprograms.utils.TMUtils;
import com.maxprograms.xml.Element;

public class Match implements Serializable, Comparable<Match> {

	private static final long serialVersionUID = -944405164833933436L;

	private Element source;
	private Element target;
	private int similarity;
	private String origin;
	private Map<String, String> properties;

	public Match(Element source, Element target, int similarity, String origin, Map<String, String> properties) {
		this.source = source;
		this.target = target;
		this.similarity = similarity;
		this.origin = origin;
		this.properties = properties;
	}

	public Element getSource() {
		return source;
	}

	public void setSource(Element source) {
		this.source = source;
	}

	public Element getTarget() {
		return target;
	}

	public void setTarget(Element target) {
		this.target = target;
	}

	public int getSimilarity() {
		return similarity;
	}

	public void setSimilarity(int similarity) {
		this.similarity = similarity;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public Map<String, String> getProperties() {
		return properties;
	}

	@Override
	public int compareTo(Match o) {
		if (similarity < o.getSimilarity()) {
			return 1;
		}
		if (similarity > o.getSimilarity()) {
			return -1;
		}
		if (getCreationDate() < o.getCreationDate()) {
			return 1;
		}
		if (getCreationDate() > o.getCreationDate()) {
			return -1;
		}
		return origin.compareTo(o.getOrigin());
	}

	private long getCreationDate() {
		String created = properties.get("creationdate");
		if (created != null) {
			return TMUtils.getGMTtime(created);
		}
		return -1l;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Match)) {
			return false;
		}
		Match m = (Match) obj;
		return source.equals(m.getSource()) && target.equals(m.getTarget()) && similarity == m.getSimilarity()
				&& origin.equals(m.getOrigin()) && properties.equals(m.getProperties());
	}

	@Override
	public int hashCode() {
		return source.hashCode() * target.hashCode() * similarity * origin.hashCode() * properties.hashCode();
	}
}
