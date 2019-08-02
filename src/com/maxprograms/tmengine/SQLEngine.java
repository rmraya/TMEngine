/*******************************************************************************
 * Copyright (c) 2003, 2018 Maxprograms.
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.tmx.TMXReader;
import com.maxprograms.utils.TMUtils;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;

public class SQLEngine implements ITmEngine {

	private static final Logger LOGGER = System.getLogger(SQLEngine.class.getName());

	private Connection conn;
	private String dbName;

	private String currProject;
	private String currSubject;
	private String currCustomer;
	private String creationDate;

	private long next;

	private TreeSet<String> languages;

	private PreparedStatement insertProperties;
	private PreparedStatement removeProperties;
	private PreparedStatement insertTuv;
	private PreparedStatement removeTuv;
	private PreparedStatement checkTu;
	private PreparedStatement selectProperties;
	private PreparedStatement selectSeg;
	private PreparedStatement selectPureText;

	private Hashtable<String, PreparedStatement> insertNgram;
	private Hashtable<String, PreparedStatement> removeNgram;
	private Hashtable<String, PreparedStatement> selectNgram;

	private Set<String> tuAttributes;

	public SQLEngine(String dbName, String serverName, int port, String userName, String password) throws SQLException {
		this.dbName = dbName;
		StringBuilder connBuilder = new StringBuilder();
		connBuilder.append("jdbc:mariadb://");
		connBuilder.append(serverName);
		connBuilder.append(':');
		connBuilder.append(port);
		connBuilder.append('/');
		connBuilder.append(dbName);
		connBuilder.append("?user=");
		connBuilder.append(userName);
		connBuilder.append("&password=");
		connBuilder.append(password);
		try {
			conn = DriverManager.getConnection(connBuilder.toString()); // "jdbc:mariadb://localhost:3306/DB?user=root&password=myPassword"
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			createDatabase(serverName, port, userName, password);
			conn = DriverManager.getConnection(connBuilder.toString());
			conn.setAutoCommit(false);
			LOGGER.log(Level.INFO, "Database " + dbName + " created.");
		}
		insertNgram = new Hashtable<>();
		removeNgram = new Hashtable<>();
		selectNgram = new Hashtable<>();
	}

	private void createDatabase(String serverName, int port, String userName, String password) throws SQLException {
		StringBuilder serverBuilder = new StringBuilder();
		serverBuilder.append("jdbc:mariadb://");
		serverBuilder.append(serverName);
		serverBuilder.append(':');
		serverBuilder.append(port);
		serverBuilder.append('/');
		Properties prop = new Properties();
		prop.setProperty("user", userName);
		prop.setProperty("password", password);
		prop.setProperty("useUnicode", "true");
		prop.setProperty("characterEncoding", StandardCharsets.UTF_8.name());
		try (Connection connection = DriverManager.getConnection(serverBuilder.toString(), prop)) {
			try (Statement stmt = connection.createStatement()) {
				stmt.execute("CREATE DATABASE `" + dbName + "` CHARACTER SET utf8");
				stmt.execute("CREATE TABLE `" + dbName + "`.tuv ( tuid VARCHAR(30) NOT NULL,"
						+ " lang VARCHAR(15) NOT NULL, seg TEXT NOT NULL, pureText TEXT,"
						+ " PRIMARY KEY (tuid,lang));");
				stmt.execute("CREATE TABLE `" + dbName + "`.tuprop ( tuid VARCHAR(30) NOT NULL,"
						+ " propType VARCHAR(30) NOT NULL, content TEXT, PRIMARY KEY (tuid, propType)" + ");");
				stmt.execute("CREATE TABLE `" + dbName + "`.langs ( lang VARCHAR(15) NOT NULL);");
			}
		}
	}

	@Override
	public void close() throws SQLException {
		conn.commit();
		if (insertProperties != null) {
			insertProperties.close();
		}
		if (removeProperties != null) {
			removeProperties.close();
		}
		if (selectProperties != null) {
			selectProperties.close();
		}
		if (insertTuv != null) {
			insertTuv.close();
		}
		if (removeTuv != null) {
			removeTuv.close();
		}
		if (checkTu != null) {
			checkTu.close();
		}
		if (selectSeg != null) {
			selectSeg.close();
		}
		if (selectPureText != null) {
			selectPureText.close();
		}
		Set<String> keys = insertNgram.keySet();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			insertNgram.get(it.next()).close();
		}
		keys = removeNgram.keySet();
		it = keys.iterator();
		while (it.hasNext()) {
			removeNgram.get(it.next()).close();
		}
		keys = selectNgram.keySet();
		it = keys.iterator();
		while (it.hasNext()) {
			selectNgram.get(it.next()).close();
		}
		conn.close();
	}

	@Override
	public String getName() {
		return dbName;
	}

	@Override
	public int storeTMX(String tmxFile, String project, String customer, String subject)
			throws SAXException, IOException, ParserConfigurationException, SQLException {
		next = 0l;
		if (customer == null) {
			customer = "";
		}
		if (subject == null) {
			subject = "";
		}
		if (project == null) {
			project = "";
		}

		currProject = project;
		currSubject = subject;
		currCustomer = customer;
		creationDate = TMUtils.creationDate();

		TMXReader reader = new TMXReader(this);
		reader.parse(new File(tmxFile).toURI().toURL());
		commit();

		return reader.getCount();
	}

	@Override
	public void exportDatabase(String tmxfile, Set<String> langs, String srcLang, Map<String, String> properties)
			throws IOException, SAXException, ParserConfigurationException, SQLException {
		if (languages == null) {
			getAllLanguages();
		}
		if (properties == null) {
			properties = new Hashtable<>();
		}
		try (FileOutputStream output = new FileOutputStream(new File(tmxfile))) {
			writeHeader(output, srcLang, properties);
			writeString(output, "  <body>\n");
			try (Statement stmt = conn.createStatement()) {
				try (ResultSet rs = stmt.executeQuery("SELECT DISTINCT tuid FROM `" + dbName + "`.tuprop")) {
					while (rs.next()) {
						String tuid = rs.getString(1);
						Element tu = getTu(tuid, langs);
						Indenter.indent(tu, 3, 2);
						writeString(output, "    " + tu.toString() + "\n");
					}
				}
			}
			writeString(output, "  </body>\n");
			writeString(output, "</tmx>");
		}
	}

	@Override
	public void flag(String tuid) throws SQLException {
		if (checkPreviousTu(tuid)) {
			Hashtable<String, String> properties = getTuProperies(tuid);
			if (!properties.containsKey("x-flag")) {
				if (insertProperties == null) {
					insertProperties = conn.prepareStatement(
							"INSERT INTO `" + dbName + "`.tuprop (tuid, propType, content) VALUES (?,?,?)");
				}
				insertProperties.setString(1, tuid);
				insertProperties.setString(2, "x-flag");
				insertProperties.setString(3, "SW-Flag");
				insertProperties.execute();
			}
		}
	}

	@Override
	public Set<String> getAllClients() throws SQLException {
		Set<String> result = new TreeSet<>();
		try (Statement stmt = conn.createStatement()) {
			try (ResultSet rs = stmt
					.executeQuery("SELECT DISTINCT content FROM `" + dbName + "`.tuprop WHERE propType='customer'")) {
				while (rs.next()) {
					result.add(rs.getNString(1));
				}
			}
		}
		return result;
	}

	@Override
	public Set<String> getAllLanguages() throws SQLException {
		if (languages == null) {
			languages = new TreeSet<String>();
			try (Statement stmt = conn.createStatement()) {
				try (ResultSet rs = stmt.executeQuery("SELECT lang FROM `" + dbName + "`.langs")) {
					while (rs.next()) {
						languages.add(rs.getString(1));
					}
				}
			}
		}
		return languages;
	}

	@Override
	public Set<String> getAllProjects() throws SQLException {
		Set<String> result = new TreeSet<>();
		try (Statement stmt = conn.createStatement()) {
			try (ResultSet rs = stmt
					.executeQuery("SELECT DISTINCT content FROM `" + dbName + "`.tuprop WHERE propType='project'")) {
				while (rs.next()) {
					result.add(rs.getNString(1));
				}
			}
		}
		return result;
	}

	@Override
	public Set<String> getAllSubjects() throws SQLException {
		Set<String> result = new TreeSet<>();
		try (Statement stmt = conn.createStatement()) {
			try (ResultSet rs = stmt
					.executeQuery("SELECT DISTINCT content FROM `" + dbName + "`.tuprop WHERE propType='subject'")) {
				while (rs.next()) {
					result.add(rs.getNString(1));
				}
			}
		}
		return result;
	}

	@Override
	public Vector<Match> searchTranslation(String searchStr, String srcLang, String tgtLang, int similarity,
			boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException, SQLException {
		Vector<Match> result = new Vector<>();

		int[] ngrams = NGrams.getNGrams(searchStr);
		int size = ngrams.length;
		if (size == 0) {
			return result;
		}

		int minLength = searchStr.length() * similarity / 100;
		int maxLength = searchStr.length() * (200 - similarity) / 100;

		String set = "" + ngrams[0];
		for (int i = 1; i < size; i++) {
			set = set + ',' + ngrams[i];
		}

		Set<String> candidates = new TreeSet<>();
		String lowerSearch = searchStr.toLowerCase();

		PreparedStatement stmt = selectNgram.get(srcLang);
		if (stmt == null) {
			stmt = conn.prepareStatement("SELECT tuid from `" + dbName + "`.matrix_"
					+ srcLang.replace('-', '_').toLowerCase() + " WHERE ngram in (?) and segSize>=? AND segSize<=?");
			selectNgram.put(srcLang, stmt);
		}
		stmt.setString(1, set);
		stmt.setInt(2, minLength);
		stmt.setInt(3, maxLength);

		try (ResultSet rs = stmt.executeQuery()) {
			while (rs.next()) {
				String tuid = rs.getString(1);
				candidates.add(tuid);
			}
		}

		Iterator<String> it = candidates.iterator();
		while (it.hasNext()) {
			String tuid = it.next();
			int distance;
			String puretext = getPureText(srcLang, tuid);
			if (caseSensitive) {
				distance = MatchQuality.similarity(searchStr, puretext);
			} else {
				distance = MatchQuality.similarity(lowerSearch, puretext.toLowerCase());
			}
			if (distance >= similarity) {
				String targetSeg = getSegText(tgtLang, tuid);
				if (targetSeg != null) {
					String sourceSeg = getSegText(srcLang, tuid);
					Element source = TMUtils.buildTuv(srcLang, sourceSeg);
					Element target = TMUtils.buildTuv(tgtLang, targetSeg);
					Hashtable<String, String> properties = getTuProperies(tuid);
					Match match = new Match(source, target, distance, dbName, properties);
					result.add(match);
				}
			}
		}
		return result;
	}

	private String getPureText(String lang, String tuid) throws SQLException {
		if (selectPureText == null) {
			selectPureText = conn.prepareStatement("SELECT pureText FROM `" + dbName + "`.tuv WHERE tuid=? AND lang=?");
		}
		String pureText = "";
		selectPureText.setString(1, tuid);
		selectPureText.setString(2, lang);
		try (ResultSet rs = selectPureText.executeQuery()) {
			while (rs.next()) {
				pureText = rs.getNString(1);
			}
		}
		return pureText;
	}

	@Override
	public Vector<Element> concordanceSearch(String searchStr, String srcLang, int limit, boolean isRegexp,
			boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException, SQLException {
		Set<String> candidates = new TreeSet<>();
		if (isRegexp) {
			try (PreparedStatement stmt = conn.prepareStatement(
					"SELECT tuid, pureText FROM `" + dbName + "`.tuv WHERE lang=? AND pureText REGEXP ? LIMIT ?")) {
				stmt.setString(1, srcLang);
				stmt.setString(2, searchStr);
				stmt.setInt(3, limit);
				try (ResultSet rs = stmt.executeQuery()) {
					while (rs.next()) {
						candidates.add(rs.getString(1));
					}
				}
			}
		} else {
			if (!caseSensitive) {
				try (PreparedStatement stmt = conn.prepareStatement("SELECT tuid, pureText FROM `" + dbName
						+ "`.tuv WHERE lang=? AND LOWER(pureText) LIKE ? LIMIT ?")) {
					stmt.setString(1, srcLang);
					stmt.setString(2, "%" + searchStr.toLowerCase() + "%");
					stmt.setInt(3, limit);
					try (ResultSet rs = stmt.executeQuery()) {
						while (rs.next()) {
							candidates.add(rs.getString(1));
						}
					}
				}
			} else {
				try (PreparedStatement stmt = conn.prepareStatement(
						"SELECT tuid, pureText FROM `" + dbName + "`.tuv WHERE lang=? AND pureText LIKE ? LIMIT ?")) {
					stmt.setString(1, srcLang);
					stmt.setString(2, "%" + searchStr + "%");
					stmt.setInt(3, limit);
					try (ResultSet rs = stmt.executeQuery()) {
						while (rs.next()) {
							candidates.add(rs.getString(1));
						}
					}
				}
			}
		}
		Vector<Element> result = new Vector<>();
		Iterator<String> it = candidates.iterator();
		while (it.hasNext()) {
			Element tu = getTu(it.next());
			result.add(tu);
		}
		return result;
	}

	@Override
	public void storeTu(Element tu) throws IOException, SQLException {
		String tuid = tu.getAttributeValue("tuid");
		boolean isNew = false;
		if (tuid.isEmpty()) {
			tuid = nextId();
			tu.setAttribute("tuid", tuid);
			isNew = true;
		}
		if (!isNew) {
			isNew = checkPreviousTu(tuid);
		}
		if (tu.getAttributeValue("creationdate").isEmpty()) {
			tu.setAttribute("creationdate", creationDate);
		}
		if (tu.getAttributeValue("creationid").isEmpty()) {
			tu.setAttribute("creationid", System.getProperty("user.name"));
		}
		Hashtable<String, String> tuProperties = new Hashtable<>();

		List<Attribute> atts = tu.getAttributes();
		Iterator<Attribute> at = atts.iterator();
		while (at.hasNext()) {
			Attribute a = at.next();
			tuProperties.put(a.getName(), a.getValue());
		}
		List<Element> properties = tu.getChildren("prop");
		Iterator<Element> kt = properties.iterator();
		while (kt.hasNext()) {
			Element prop = kt.next();
			tuProperties.put(prop.getAttributeValue("type"), prop.getText());
		}
		if (currSubject != null && !currSubject.isEmpty()) {
			tuProperties.put("subject", currSubject);
		}
		if (currCustomer != null && !currCustomer.isEmpty()) {
			tuProperties.put("customer", currCustomer);
		}
		if (currProject != null && !currProject.isEmpty()) {
			tuProperties.put("project", currProject);
		}
		List<Element> tuvs = tu.getChildren("tuv");
		Set<String> tuLangs = new TreeSet<>();

		Iterator<Element> it = tuvs.iterator();
		while (it.hasNext()) {
			Element tuv = it.next();
			String lang = TMUtils.normalizeLang(tuv.getAttributeValue("xml:lang"));
			if (lang == null) {
				// Invalid language code, ignore this tuv
				continue;
			}
			storeLanguage(lang);
			if (!isNew) {
				removeTuv(lang, tuid);
			}
			if (!tuLangs.contains(lang)) {
				Element seg = tuv.getChild("seg");
				String puretext = TMUtils.extractText(seg);
				if (puretext.isBlank()) {
					continue;
				}

				String segText = seg.toString();
				segText = segText.substring("<seg>".length());
				segText = segText.substring(0, segText.length() - "</seg>".length());
				storeTuv(lang, tuid, puretext, segText);

				int[] ngrams = NGrams.getNGrams(puretext);
				storeNgrams(lang, tuid, ngrams, puretext.length());

				tuLangs.add(lang);
			}
		}
		if (!isNew) {
			removeTuProperties(tuid);
		}
		storeTuProperties(tuid, tuProperties);
	}

	private boolean checkPreviousTu(String tuid) throws SQLException {
		if (checkTu == null) {
			checkTu = conn.prepareStatement("SELECT COUNT(*) FROM `" + dbName + "`.tuprop WHERE tuid=?");
		}
		checkTu.setString(1, tuid);
		int count = 0;
		try (ResultSet rs = checkTu.executeQuery()) {
			while (rs.next()) {
				count = rs.getInt(1);
			}
		}
		return count != 0;
	}

	private void storeNgrams(String lang, String tuid, int[] ngrams, int segSize) throws SQLException {
		PreparedStatement stmt = insertNgram.get(lang);
		if (stmt == null) {
			stmt = conn.prepareStatement("INSERT INTO `" + dbName + "`.matrix_" + lang.replace('-', '_').toLowerCase()
					+ " (tuid, ngram, segSize) VALUES (?,?,?)");
			insertNgram.put(lang, stmt);
		}
		stmt.setString(1, tuid);
		stmt.setInt(3, segSize);
		for (int i = 0; i < ngrams.length; i++) {
			stmt.setInt(2, ngrams[i]);
			stmt.execute();
		}
	}

	private void storeTuv(String lang, String tuid, String puretext, String segText) throws SQLException {
		if (insertTuv == null) {
			insertTuv = conn
					.prepareStatement("INSERT INTO `" + dbName + "`.tuv (lang, tuid, seg, pureText ) VALUES (?,?,?,?)");
		}
		insertTuv.setString(1, lang);
		insertTuv.setString(2, tuid);
		insertTuv.setNString(3, segText);
		insertTuv.setNString(4, puretext);
		insertTuv.execute();
	}

	private void removeTuProperties(String tuid) throws SQLException {
		if (removeProperties == null) {
			removeProperties = conn.prepareStatement("DELETE FROM `" + dbName + "`.tuprop WHERE tuid=?");
		}
		removeProperties.setString(1, tuid);
		removeProperties.execute();
	}

	private void storeTuProperties(String tuid, Hashtable<String, String> properties) throws SQLException {
		if (insertProperties == null) {
			insertProperties = conn
					.prepareStatement("INSERT INTO `" + dbName + "`.tuprop (tuid, propType, content) VALUES (?,?,?)");
		}
		insertProperties.setString(1, tuid);
		Set<String> keys = properties.keySet();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			String prop = it.next();
			insertProperties.setString(2, prop);
			insertProperties.setNString(3, properties.get(prop));
			insertProperties.execute();
		}
	}

	private void removeTuv(String lang, String tuid) throws SQLException {
		if (removeTuv == null) {
			removeTuv = conn.prepareStatement("DELETE FROM `" + dbName + "`.tuv WHERE tuid=? AND lang=?");
		}
		removeTuv.setString(1, tuid);
		removeTuv.setString(2, lang);
		removeTuv.execute();
		PreparedStatement stmt = removeNgram.get(lang);
		if (stmt == null) {
			stmt = conn.prepareStatement(
					"DELETE FROM  `" + dbName + "`.matrix_" + lang.replace('-', '_').toLowerCase() + " WHERE tuid=?");
			removeNgram.put(lang, stmt);
		}
		stmt.setString(1, tuid);
		stmt.execute();
	}

	private void storeLanguage(String lang) throws SQLException {
		if (languages == null) {
			getAllLanguages();
		}
		if (!languages.contains(lang)) {
			try (PreparedStatement stmt = conn
					.prepareStatement("INSERT INTO `" + dbName + "`.langs (lang) VALUES (?)")) {
				stmt.setString(1, lang);
				stmt.execute();
			}
			try (Statement stmt = conn.createStatement()) {
				stmt.execute("CREATE TABLE `" + dbName + "`.matrix_" + lang.replace('-', '_').toLowerCase() + " ("
						+ " tuid VARCHAR(30) NOT NULL, ngram INTEGER NOT NULL, segSize INTEGER,"
						+ " INDEX `ngrams` (`ngram` ASC) VISIBLE, PRIMARY KEY (tuid,ngram));");
			}
			conn.commit();
			languages.add(lang);
		}
	}

	@Override
	public void commit() throws SQLException {
		conn.commit();
	}

	private Element getTu(String tuid, Set<String> langs)
			throws SQLException, SAXException, IOException, ParserConfigurationException {
		if (tuAttributes == null) {
			tuAttributes = new TreeSet<>();
			String[] array = new String[] { "tuid", "o-encoding", "datatype", "usagecount", "lastusagedate",
					"creationtool", "creationtoolversion", "creationdate", "creationid", "changedate", "segtype",
					"changeid", "o-tmf", "srclang" };
			for (int i = 0; i < array.length; i++) {
				tuAttributes.add(array[i]);
			}
		}
		Hashtable<String, String> properties = getTuProperies(tuid);
		Element tu = new Element("tu");
		Set<String> keys = properties.keySet();
		Iterator<String> it = keys.iterator();
		while (it.hasNext()) {
			String key = it.next();
			if (tuAttributes.contains(key)) {
				tu.setAttribute(key, properties.get(key));
			} else {
				Element prop = new Element("prop");
				prop.setAttribute("type", key);
				prop.setText(properties.get(key));
				tu.addContent(prop);
			}
		}
		if (langs.isEmpty()) {
			langs = getAllLanguages();
		}
		it = langs.iterator();
		while (it.hasNext()) {
			String lang = it.next();
			String seg = getSegText(lang, tuid);
			if (!seg.isEmpty()) {
				Element tuv = TMUtils.buildTuv(lang, seg);
				tu.addContent(tuv);
			}
		}
		return tu;
	}

	@Override
	public Element getTu(String tuid) throws IOException, SAXException, ParserConfigurationException, SQLException {
		return getTu(tuid, new TreeSet<>());
	}

	private String getSegText(String lang, String tuid) throws SQLException {
		if (selectSeg == null) {
			selectSeg = conn.prepareStatement("SELECT seg FROM  `" + dbName + "`.tuv  WHERE tuid=? AND lang=?");
		}
		String seg = "";
		selectSeg.setString(1, tuid);
		selectSeg.setString(2, lang);
		try (ResultSet rs = selectSeg.executeQuery()) {
			while (rs.next()) {
				seg = rs.getNString(1);
			}
		}
		return seg;
	}

	private Hashtable<String, String> getTuProperies(String tuid) throws SQLException {
		if (selectProperties == null) {
			selectProperties = conn
					.prepareStatement("SELECT propType, content FROM `" + dbName + "`.tuprop WHERE tuid=?");
		}
		selectProperties.setString(1, tuid);
		Hashtable<String, String> properties = new Hashtable<>();
		try (ResultSet rs = selectProperties.executeQuery()) {
			while (rs.next()) {
				properties.put(rs.getString(1), rs.getNString(2));
			}
		}
		return properties;
	}

	private String nextId() {
		if (next == 0l) {
			next = Calendar.getInstance().getTimeInMillis();
		}
		return "" + next++;
	}

	private static void writeHeader(FileOutputStream output, String srcLang, Map<String, String> properties)
			throws IOException {
		writeString(output, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writeString(output,
				"<!DOCTYPE tmx PUBLIC \"-//LISA OSCAR:1998//DTD for Translation Memory eXchange//EN\" \"tmx14.dtd\" >\n");
		writeString(output, "<tmx version=\"1.4\">\n");
		writeString(output,
				"  <header creationtool=\"" + Constants.CREATIONTOOL + "\" creationtoolversion=\"" + Constants.VERSION
						+ "\" srclang=\"" + srcLang
						+ "\" adminlang=\"en\" datatype=\"xml\" o-tmf=\"unknown\" segtype=\"block\" creationdate=\""
						+ TMUtils.creationDate() + "\"");
		if (properties.isEmpty()) {
			writeString(output, "/>\n");
		} else {
			writeString(output, ">\n");
			Set<String> keys = properties.keySet();
			Iterator<String> it = keys.iterator();
			while (it.hasNext()) {
				String key = it.next();
				writeString(output, "   <prop type=\"" + key + "\">" + properties.get(key) + "</prop>\n");
			}
			writeString(output, "  </header>\n");
		}
	}

	private static void writeString(FileOutputStream output, String string) throws IOException {
		output.write(string.getBytes("UTF-8"));
	}

	@Override
	public void removeTu(String tuid) throws IOException, SAXException, ParserConfigurationException, SQLException {
		Element tu = getTu(tuid);
		removeTuProperties(tuid);
		List<Element> tuvs = tu.getChildren("tuv");

		Iterator<Element> it = tuvs.iterator();
		while (it.hasNext()) {
			Element tuv = it.next();
			String lang = tuv.getAttributeValue("xml:lang");
			removeTuv(lang, tuid);
		}
	}
}
