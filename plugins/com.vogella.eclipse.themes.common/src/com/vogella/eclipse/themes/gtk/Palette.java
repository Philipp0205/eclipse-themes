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
import java.util.Optional;
import java.util.Properties;

/**
 * The token values of one theme, keyed by theme id.
 * <p>
 * The values are read from {@code palettes/<theme id>.properties} in this bundle
 * rather than from the workbench colour registry, and the reason is ordering.
 * {@code ThemeEngine.setTheme} posts {@code THEME_CHANGED} and only then calls
 * {@code reapply()} on its CSS engines, so at the moment this code learns which
 * theme is active the palette's {@code ColorDefinition} rules have not run and the
 * registry still holds the previous theme's colours. Reading a file cannot be early.
 * <p>
 * That the file and the palette stylesheet agree is a build time property rather
 * than a runtime one: {@code releng/check-gtk-palettes.sh} compares every value
 * against the {@code ColorDefinition} it was copied from and fails the build on any
 * difference, the same way {@code releng/check-tokens.sh} polices the token names.
 */
final class Palette {

	private final Properties values;

	private Palette(Properties values) {
		this.values = values;
	}

	/**
	 * The palette of the given theme, or empty when this bundle ships none, which is
	 * the normal answer for every theme that is not one of ours.
	 */
	static Optional<Palette> of(String themeId) throws IOException {
		String resource = "/palettes/" + themeId + ".properties";
		try (InputStream stream = Palette.class.getResourceAsStream(resource)) {
			if (stream == null) {
				return Optional.empty();
			}
			Properties values = new Properties();
			values.load(stream);
			return Optional.of(new Palette(values));
		}
	}

	/**
	 * The value of one token, or empty when the file does not carry it. An absent
	 * token has to stop the whole sheet rather than resolve to something: GTK would
	 * read a leftover placeholder as an invalid colour and drop the rule, which is a
	 * silent hole in the middle of the theme.
	 */
	Optional<String> value(String token) {
		String value = values.getProperty(token);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
	}
}
