/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     vogella GmbH - initial API and implementation
 *******************************************************************************/
package com.vogella.eclipse.themes.gtk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code css/gtk.css} against a {@link Palette}.
 * <p>
 * The template carries the repository's own token spelling,
 * {@code '#com-vogella-themes-BG_RAISED'}, so that {@code releng/check-tokens.sh}
 * reads it as one more consumer and holds it to the same contract as
 * {@code structure.css}. Substitution is therefore all this class does.
 */
final class GtkStyleSheet {

	private static final String TEMPLATE = "/css/gtk.css";

	/** The token spelling shared with every other stylesheet in the repository. */
	private static final Pattern TOKEN = Pattern.compile("'#com-vogella-themes-([A-Za-z_0-9]+)'");

	private GtkStyleSheet() {
	}

	/**
	 * The template with every token replaced by its value in the given palette.
	 *
	 * @throws IllegalStateException if the template names a token the palette does not
	 *                               carry. Leaving the placeholder in place would make
	 *                               GTK drop that one rule and nothing else, so the
	 *                               theme would come out almost right, which is worse
	 *                               than not applying it at all.
	 */
	static String resolve(Palette palette) throws IOException {
		String template = read();
		Set<String> missing = new LinkedHashSet<>();
		Matcher matcher = TOKEN.matcher(template);
		StringBuilder resolved = new StringBuilder();
		while (matcher.find()) {
			String token = matcher.group(1);
			String value = palette.value(token).orElse(null);
			if (value == null) {
				missing.add(token);
				value = "";
			}
			matcher.appendReplacement(resolved, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(resolved);
		if (!missing.isEmpty()) {
			throw new IllegalStateException("palette carries no value for " + missing);
		}
		return resolved.toString();
	}

	private static String read() throws IOException {
		try (InputStream stream = GtkStyleSheet.class.getResourceAsStream(TEMPLATE)) {
			if (stream == null) {
				throw new IOException(TEMPLATE + " is missing from the bundle");
			}
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
