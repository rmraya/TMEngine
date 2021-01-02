/*******************************************************************************
 * Copyright (c) 2003-2021 Maxprograms.
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

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.Vector;

public class NGrams {

	private static final int NGRAMSIZE = 3;
	public static final String SEPARATORS = " \r\n\f\t\u2028\u2029,.;\":<>¿?¡!()[]{}=+-/*\u00AB\u00BB\u201C\u201D\u201E\uFF00";
	// allow hyphen in terms
	public static final String TERM_SEPARATORS = " \u00A0\r\n\f\t\u2028\u2029,.;\":<>¿?¡!()[]{}=+/*\u00AB\u00BB\u201C\u201D\u201E\uFF00";

	private NGrams() {
		// private for security
	}
	
	public static int[] getNGrams(String string) {
		String src = string.toLowerCase();
		List<String> words = buildWordList(src);
		Set<String> set = Collections.synchronizedSortedSet(new TreeSet<>());
		
		Iterator<String> it = words.iterator();
		while (it.hasNext()) {
			String word = it.next();
			char[] array = word.toCharArray();
			int length = word.length();
			int ngrams = length / NGRAMSIZE;
			if (ngrams * NGRAMSIZE < length) {
				ngrams++;
			}
			for (int i = 0; i < ngrams; i++) {
				StringBuilder gram = new StringBuilder();
				for (int j = 0; j < NGRAMSIZE; j++) {
					if (i * NGRAMSIZE + j < length) {
						gram.append(array[i * NGRAMSIZE + j]);
					}
				}
				set.add("" + gram.toString().hashCode());
			}
		}
		
		int[] result = new int[set.size()];
		int idx = 0;
		it = set.iterator();
		while (it.hasNext()) {
			result[idx++] = Integer.parseInt(it.next());
		}
		return result;
	}

	private static List<String> buildWordList(String src) {
		List<String> result = new Vector<>();
		StringTokenizer tokenizer = new StringTokenizer(src, SEPARATORS);
		while (tokenizer.hasMoreElements()) {
			result.add(tokenizer.nextToken());
		}
		return result;
	}

}
