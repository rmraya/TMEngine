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
package com.maxprograms.tmutils;

import com.maxprograms.languages.RegistryParser;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.parsers.ParserConfigurationException;

public class TMUtils {

	private static Logger logger = System.getLogger(TMUtils.class.getName());
	private static RegistryParser registry;

	private TMUtils() {
		// private for security
	}

	public static String pureText(Element seg) {
		List<XMLNode> l = seg.getContent();
		Iterator<XMLNode> i = l.iterator();
		StringBuilder text = new StringBuilder();
		while (i.hasNext()) {
			XMLNode o = i.next();
			if (o.getNodeType() == XMLNode.TEXT_NODE) {
				text.append(((TextNode) o).getText());
			} else if (o.getNodeType() == XMLNode.ELEMENT_NODE) {
				String type = ((Element) o).getName();
				// discard all inline elements
				// except <mrk> and <hi>
				if (type.equals("sub") || type.equals("hi")) {
					Element e = (Element) o;
					text.append(pureText(e));
				}
			}
		}
		return text.toString();
	}

	public static String normalizeLang(String langCode) throws IOException {
		if (registry == null) {
			registry = new RegistryParser();
		}
		if (langCode == null) {
			return null;
		}
		if (!registry.getTagDescription(langCode).isEmpty()) {
			return langCode;
		}
		String lang = langCode.replaceAll("_", "-");
		String[] parts = lang.split("-");

		if (parts.length == 2) {
			if (parts[1].length() == 2) {
				// has country code
				String code = lang.substring(0, 2).toLowerCase() + "-" + lang.substring(3).toUpperCase();
				if (!registry.getTagDescription(code).isEmpty()) {
					return code;
				}
				return null;
			}
			// may have a script
			String code = lang.substring(0, 2).toLowerCase() + "-" + lang.substring(3, 4).toUpperCase()
					+ lang.substring(4).toLowerCase();
			if (!registry.getTagDescription(code).isEmpty()) {
				return code;
			}
			return null;
		}
		// check if its a valid thing with more than 2 parts
		if (!registry.getTagDescription(lang).isEmpty()) {
			return lang;
		}
		return null;
	}

	public static String createId() throws InterruptedException {
		Date now = new Date();
		long lng = now.getTime();
		// wait until we are in the next millisecond
		// before leaving to ensure uniqueness
		Thread.sleep(1);
		return "" + lng;
	}

	public static String tmxDate() {
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		String sec = (calendar.get(Calendar.SECOND) < 10 ? "0" : "") + calendar.get(Calendar.SECOND);
		String min = (calendar.get(Calendar.MINUTE) < 10 ? "0" : "") + calendar.get(Calendar.MINUTE);
		String hour = (calendar.get(Calendar.HOUR_OF_DAY) < 10 ? "0" : "") + calendar.get(Calendar.HOUR_OF_DAY);
		String mday = (calendar.get(Calendar.DATE) < 10 ? "0" : "") + calendar.get(Calendar.DATE);
		String mon = (calendar.get(Calendar.MONTH) < 9 ? "0" : "") + (calendar.get(Calendar.MONTH) + 1);
		String longyear = "" + calendar.get(Calendar.YEAR);

		return longyear + mon + mday + "T" + hour + min + sec + "Z";
	}

	public static long getGMTtime(String tmxDate) {
		Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
		try {
			int second = Integer.parseInt(tmxDate.substring(13, 15));
			int minute = Integer.parseInt(tmxDate.substring(11, 13));
			int hour = Integer.parseInt(tmxDate.substring(9, 11));
			int date = Integer.parseInt(tmxDate.substring(6, 8));
			int month = Integer.parseInt(tmxDate.substring(4, 6)) - 1;
			int year = Integer.parseInt(tmxDate.substring(0, 4));
			calendar.set(year, month, date, hour, minute, second);
			return calendar.getTimeInMillis();
		} catch (NumberFormatException e) {
			logger.log(Level.WARNING, "Unsupported TMX date: " + tmxDate);
			return 0l;
		}
	}

	public static Element buildTuv(String lang, String seg)
			throws SAXException, IOException, ParserConfigurationException {
		Element tuv = new Element("tuv");
		tuv.setAttribute("xml:lang", lang);
		SAXBuilder builder = new SAXBuilder();
		Element e = builder.build(new ByteArrayInputStream(("<seg>" + seg + "</seg>").getBytes(StandardCharsets.UTF_8)))
				.getRootElement();
		tuv.addContent(e);
		return tuv;
	}

	public static String creationDate() {
		Calendar calendar = Calendar.getInstance(Locale.US);
		String sec = (calendar.get(Calendar.SECOND) < 10 ? "0" : "") + calendar.get(Calendar.SECOND);
		String min = (calendar.get(Calendar.MINUTE) < 10 ? "0" : "") + calendar.get(Calendar.MINUTE);
		String hour = (calendar.get(Calendar.HOUR_OF_DAY) < 10 ? "0" : "") + calendar.get(Calendar.HOUR_OF_DAY);
		String mday = (calendar.get(Calendar.DATE) < 10 ? "0" : "") + calendar.get(Calendar.DATE);
		String mon = (calendar.get(Calendar.MONTH) < 9 ? "0" : "") + (calendar.get(Calendar.MONTH) + 1);
		String longyear = "" + calendar.get(Calendar.YEAR);

		return longyear + mon + mday + "T" + hour + min + sec + "Z";
	}

	public static String extractText(Element seg) {
		List<XMLNode> l = seg.getContent();
		Iterator<XMLNode> i = l.iterator();
		StringBuilder text = new StringBuilder();
		while (i.hasNext()) {
			XMLNode o = i.next();
			if (o.getNodeType() == XMLNode.TEXT_NODE) {
				text.append(((TextNode) o).getText());
			} else if (o.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) o;
				String type = e.getName();
				// discard all inline elements
				// except <sub> and <hi>
				if (type.equals("sub") || type.equals("hi")) {
					text.append(extractText(e));
				}
			}
		}
		return text.toString();
	}
}
