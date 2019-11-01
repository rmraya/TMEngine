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

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.xml.Element;

public interface ITmEngine {

	public abstract String getType();

	public abstract void close() throws IOException, SQLException;

	public abstract String getName();

	public abstract int storeTMX(String tmxFile, String project, String customer, String subject)
			throws SAXException, IOException, ParserConfigurationException, SQLException;

	public abstract void exportMemory(String tmxfile, Set<String> langs, String srcLang, Map<String, String> properties)
			throws IOException, SAXException, ParserConfigurationException, SQLException;

	public abstract void flag(String tuid) throws SQLException;

	public abstract Set<String> getAllClients() throws SQLException;

	public abstract Set<String> getAllLanguages() throws SQLException;

	public abstract Set<String> getAllProjects() throws SQLException;

	public abstract Set<String> getAllSubjects() throws SQLException;

	public abstract List<Match> searchTranslation(String searchStr, String srcLang, String tgtLang, int similarity,
			boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException, SQLException;

	public abstract List<Element> concordanceSearch(String searchStr, String srcLang, int limit, boolean isRegexp,
			boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException, SQLException;

	public abstract void storeTu(Element tu) throws IOException, SQLException;

	public abstract void commit() throws SQLException;

	public abstract Element getTu(String tuid)
			throws IOException, SAXException, ParserConfigurationException, SQLException;

	public abstract void removeTu(String tuid)
			throws IOException, SAXException, ParserConfigurationException, SQLException;

	public abstract void deleteDatabase() throws IOException, SQLException;
}
