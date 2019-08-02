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
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.parsers.ParserConfigurationException;

import org.mapdb.Fun;
import org.mapdb.Fun.Tuple2;
import org.xml.sax.SAXException;

import com.maxprograms.tmx.TMXReader;
import com.maxprograms.utils.TMUtils;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.Indenter;

public class MapDbEngine implements ITmEngine, AutoCloseable {

	private static final Logger LOGGER = System.getLogger(MapDbEngine.class.getName());

	private String dbname;
	private File database;
	private TuDatabase tuDb;
	private TuvDatabase tuvDb;
	private FuzzyIndex fuzzyIndex;

	private String currProject;
	private String currSubject;
	private String currCustomer;
	private String creationDate;

	private Set<String> tuAttributes;

	private long next;

	public MapDbEngine(String dbname, String workFolder) throws IOException {
		this.dbname = dbname;
		tuAttributes = new TreeSet<>();
		String[] array = new String[] { "tuid", "o-encoding", "datatype", "usagecount", "lastusagedate", "creationtool",
				"creationtoolversion", "creationdate", "creationid", "changedate", "segtype", "changeid", "o-tmf",
				"srclang" };
		for (int i = 0; i < array.length; i++) {
			tuAttributes.add(array[i]);
		}
		File wfolder = new File(workFolder);
		database = new File(wfolder, dbname);
		if (!database.exists()) {
			database.mkdirs();
		}
		try {
			tuDb = new TuDatabase(database);
		} catch (Exception e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
			MessageFormat mf = new MessageFormat("TU storage of database {0} is damaged.");
			throw new IOException(mf.format(new String[] { dbname }));
		}
		try {
			tuvDb = new TuvDatabase(database);
		} catch (Exception e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
			MessageFormat mf = new MessageFormat("TUV storage of database {0} is damaged.");
			throw new IOException(mf.format(new String[] { dbname }));
		}
		try {
			fuzzyIndex = new FuzzyIndex(database);
		} catch (Exception e) {
			LOGGER.log(Level.ERROR, e.getMessage(), e);
			MessageFormat mf = new MessageFormat("Fuzzy index of database {0} is damaged.");
			throw new IOException(mf.format(new String[] { dbname }));
		}
	}

	@Override
	public void close() throws IOException {
		fuzzyIndex.close();
		tuDb.close();
		tuvDb.close();
	}

	@Override
	public String getName() {
		return dbname;
	}

	@Override
	public int storeTMX(String tmxFile, String project, String customer, String subject)
			throws SAXException, IOException, ParserConfigurationException {
		next = 0l;
		currProject = project != null ? project : "";
		currSubject = subject != null ? subject : "";
		currCustomer = customer != null ? customer : "";
		creationDate = TMUtils.creationDate();

		TMXReader reader = new TMXReader(this);
		reader.parse(new File(tmxFile).toURI().toURL());
		commit();

		return reader.getCount();
	}

	@Override
	public void exportDatabase(String tmxfile, Set<String> langs, String srcLang, Map<String, String> props)
			throws IOException, SAXException, ParserConfigurationException {
		Map<String, String> properties = props != null ? props : new Hashtable<>();
		try (FileOutputStream output = new FileOutputStream(new File(tmxfile))) {
			writeHeader(output, srcLang, properties);
			writeString(output, "  <body>\n");

			Set<Integer> set = tuDb.getKeys();
			Iterator<Integer> it = set.iterator();
			while (it.hasNext()) {
				int hash = it.next();
				Map<String, String> tuProps = tuDb.getTu(hash);
				Element tu = buildElement(tuProps);
				if (langs != null) {
					List<Element> tuvs = tu.getChildren("tuv");
					Iterator<Element> et = tuvs.iterator();
					while (it.hasNext()) {
						Element tuv = et.next();
						if (!langs.contains(tuv.getAttributeValue("xml:lang"))) {
							tu.removeChild(tuv);
						}
					}
				}
				Indenter.indent(tu, 3, 2);
				writeString(output, "    " + tu.toString() + "\n");
			}
			writeString(output, "  </body>\n");
			writeString(output, "</tmx>");
		}
	}

	@Override
	public void flag(String tuid) {
		Map<String, String> properties = tuDb.getTu(tuid.hashCode());
		if (properties != null) {
			properties.put("x-flag", "SW-Flag");
			tuDb.store(tuid, properties);
		}
	}

	@Override
	public Set<String> getAllClients() {
		return tuDb.getCustomers();
	}

	@Override
	public Set<String> getAllLanguages() {
		return tuDb.getLanguages();
	}

	@Override
	public Set<String> getAllProjects() {
		return tuDb.getProjects();
	}

	@Override
	public Set<String> getAllSubjects() {
		return tuDb.getSubjects();
	}

	@Override
	public Vector<Match> searchTranslation(String searchStr, String srcLang, String tgtLang, int similarity,
			boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException {

		Vector<Match> result = new Vector<>();

		if (similarity == 100) {
			// check for perfect matches
			Set<String> perfect = tuvDb.getPerfectMatches(srcLang, searchStr);
			if (!perfect.isEmpty()) {
				Iterator<String> it = perfect.iterator();
				while (it.hasNext()) {
					String tuid = it.next();
					String puretext = tuvDb.getPureText(srcLang, tuid.hashCode());
					boolean isMatch = true;
					if (caseSensitive) {
						isMatch = searchStr.equals(puretext);
					}
					if (isMatch) {
						String targetSeg = tuvDb.getSegText(tgtLang, tuid);
						if (targetSeg != null) {
							String sourceSeg = tuvDb.getSegText(srcLang, tuid);
							Element source = TMUtils.buildTuv(srcLang, sourceSeg);
							Element target = TMUtils.buildTuv(tgtLang, targetSeg);
							Map<String, String> properties = tuDb.getTu(tuid.hashCode());
							Match match = new Match(source, target, 100, dbname, properties);
							result.add(match);
						}
					}
				}
			}
		}
		if (similarity < 100) {
			// Check for fuzzy matches
			int[] ngrams = NGrams.getNGrams(searchStr);
			int size = ngrams.length;
			if (size == 0) {
				return result;
			}
			int min = size * similarity / 100;
			int max = size * (200 - similarity) / 100;

			Map<String, Integer> candidates = new HashMap<>();
			String lowerSearch = searchStr.toLowerCase();

			NavigableSet<Fun.Tuple2<Integer, String>> index = fuzzyIndex.getIndex(srcLang);
			for (int i = 0; i < ngrams.length; i++) {
				Iterable<String> keys = Fun.filter(index, ngrams[i]);
				Iterator<String> it = keys.iterator();
				while (it.hasNext()) {
					String tuid = it.next();
					if (candidates.containsKey(tuid)) {
						int count = candidates.get(tuid);
						candidates.put(tuid, count + 1);
					} else {
						candidates.put(tuid, 1);
					}
				}
			}

			Set<String> tuids = candidates.keySet();
			Iterator<String> it = tuids.iterator();
			while (it.hasNext()) {
				String tuid = it.next();
				int count = candidates.get(tuid);
				if (count >= min && count <= max) {
					int distance;
					String puretext = tuvDb.getPureText(srcLang, tuid.hashCode());
					if (caseSensitive) {
						distance = MatchQuality.similarity(searchStr, puretext);
					} else {
						distance = MatchQuality.similarity(lowerSearch, puretext.toLowerCase());
					}
					if (distance >= similarity) {
						String targetSeg = tuvDb.getSegText(tgtLang, tuid);
						if (targetSeg != null) {
							String sourceSeg = tuvDb.getSegText(srcLang, tuid);
							Element source = TMUtils.buildTuv(srcLang, sourceSeg);
							Element target = TMUtils.buildTuv(tgtLang, targetSeg);
							Map<String, String> properties = tuDb.getTu(tuid.hashCode());
							Match match = new Match(source, target, distance, dbname, properties);
							result.add(match);
						}
					}
				}
			}
		}
		return result;
	}

	private Element buildElement(Map<String, String> properties)
			throws IOException, SAXException, ParserConfigurationException {
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
		String tuid = tu.getAttributeValue("tuid");
		Set<String> langs = tuDb.getLanguages();
		it = langs.iterator();
		while (it.hasNext()) {
			String lang = it.next();
			String seg = tuvDb.getSegText(lang, tuid);
			if (seg != null) {
				Element tuv = TMUtils.buildTuv(lang, seg);
				tu.addContent(tuv);
			}
		}
		return tu;
	}

	@Override
	public Vector<Element> concordanceSearch(String searchStr, String srcLang, int limit, boolean isRegexp,
			boolean caseSensitive) throws IOException, SAXException, ParserConfigurationException {
		Vector<Element> result = new Vector<>();
		Pattern pattern = null;
		if (isRegexp) {
			try {
				pattern = Pattern.compile(searchStr);
			} catch (PatternSyntaxException pse) {
				throw new IOException(pse.getMessage());
			}
		}
		String lowerStr = searchStr.toLowerCase();
		NavigableSet<Integer> keySet = tuvDb.getKeySet(srcLang);
		Iterator<Integer> it = keySet.iterator();
		while (it.hasNext()) {
			int hash = it.next();
			String pureText = tuvDb.getPureText(srcLang, hash);
			if (isRegexp) {
				if (pattern != null && pattern.matcher(pureText).matches()) {
					result.add(buildElement(tuDb.getTu(hash)));
					if (result.size() == limit) {
						return result;
					}
				}
			} else {
				if (caseSensitive) {
					if (pureText.indexOf(searchStr) != -1) {
						result.add(buildElement(tuDb.getTu(hash)));
						if (result.size() == limit) {
							return result;
						}
					}
				} else {
					if (pureText.toLowerCase().indexOf(lowerStr) != -1) {
						result.add(buildElement(tuDb.getTu(hash)));
						if (result.size() == limit) {
							return result;
						}
					}
				}
			}

		}
		return result;
	}

	@Override
	public void storeTu(Element tu) throws IOException {
		String tuid = tu.getAttributeValue("tuid");
		if (tuid.isEmpty()) {
			tuid = nextId();
			tu.setAttribute("tuid", tuid);
		}
		if (tu.getAttributeValue("creationdate").isEmpty()) {
			tu.setAttribute("creationdate", creationDate);
		}
		if (tu.getAttributeValue("creationid").isEmpty()) {
			tu.setAttribute("creationid", System.getProperty("user.name"));
		}
		Map<String, String> tuProperties = new HashMap<>();

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
			tuDb.storeLanguage(lang);
			tuvDb.remove(lang, tuid);
			if (!tuLangs.contains(lang)) {
				Element seg = tuv.getChild("seg");
				String puretext = TMUtils.extractText(seg);
				if (!puretext.isBlank()) {
					String segText = seg.toString();
					segText = segText.substring("<seg>".length());
					segText = segText.substring(0, segText.length() - "</seg>".length());
					tuvDb.store(lang, tuid, puretext, segText);

					int[] ngrams = NGrams.getNGrams(puretext);
					NavigableSet<Fun.Tuple2<Integer, String>> index = fuzzyIndex.getIndex(lang);
					for (int i = 0; i < ngrams.length; i++) {
						Tuple2<Integer, String> entry = Fun.t2(ngrams[i], tuid);
						if (!index.contains(entry)) {
							index.add(entry);
						}
					}
					tuLangs.add(lang);
				}
			}
		}
		tuDb.store(tuid, tuProperties);
	}

	@Override
	public void commit() {
		fuzzyIndex.commit();
		tuDb.commit();
		tuvDb.commit();
	}

	@Override
	public Element getTu(String tuid) throws IOException, SAXException, ParserConfigurationException {
		Map<String, String> properties = tuDb.getTu(tuid.hashCode());
		return buildElement(properties);
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
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private String nextId() {
		if (next == 0l) {
			next = Calendar.getInstance().getTimeInMillis();
		}
		return "" + next++;
	}

	@Override
	public void removeTu(String tuid) throws IOException, SAXException, ParserConfigurationException {
		Element tu = getTu(tuid);
		tuDb.remove(tuid);

		List<Element> tuvs = tu.getChildren("tuv");
		Iterator<Element> it = tuvs.iterator();
		while (it.hasNext()) {
			Element tuv = it.next();
			String lang = tuv.getAttributeValue("xml:lang");
			tuvDb.remove(lang, tuid);

			Element seg = tuv.getChild("seg");
			String puretext = TMUtils.extractText(seg);

			int[] ngrams = NGrams.getNGrams(puretext);
			NavigableSet<Fun.Tuple2<Integer, String>> index = fuzzyIndex.getIndex(lang);
			for (int i = 0; i < ngrams.length; i++) {
				Tuple2<Integer, String> entry = Fun.t2(ngrams[i], tuid);
				if (index.contains(entry)) {
					index.remove(entry);
				}
			}
		}
	}

}
