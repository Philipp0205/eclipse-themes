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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.swt.widgets.Display;

/**
 * The single {@code GtkCssProvider} these themes own, and the bridge to it.
 * <p>
 * <b>Why reflection.</b> The functions needed here live in SWT's
 * {@code org.eclipse.swt.internal.gtk*} packages, and their home has moved before:
 * {@code GTK3} and {@code GTK4} used to sit in {@code org.eclipse.swt.internal.gtk}
 * and now sit in {@code org.eclipse.swt.internal.gtk3} and
 * {@code org.eclipse.swt.internal.gtk4}. Importing them would tie this bundle to one
 * SWT layout and break the theme on the next one; looking them up by name lets a
 * layout we have not seen fail into a log entry, with the IDE still themed
 * everywhere the e4 engine reaches. There is no supported alternative: SWT's own
 * {@code -Dorg.eclipse.swt.internal.gtk.cssFile} is read once during {@code Device}
 * init, so it lives in {@code eclipse.ini} and cannot follow a theme switch.
 * <p>
 * <b>Why PRIORITY_USER.</b> GTK resolves a property across providers by priority
 * before specificity, and SWT installs its per widget CSS at
 * {@code PRIORITY_APPLICATION}, 600, including the
 * {@code treeview:selected {background-color: <desktop accent>}} that
 * {@code Tree.setBackgroundGdkRGBA} re-asserts. Measured on GTK 3.24 against a live
 * {@code GtkTreeView}: a screen provider at 201 loses to that rule, a screen
 * provider at {@code PRIORITY_USER}, 800, wins. Anything below 800 would leave the
 * selection exactly as it is today, which is the whole reason this bundle exists.
 * The price is that a declaration here also outranks the e4 cascade, so
 * {@code css/gtk.css} is restricted to what SWT provably never emits.
 * <p>
 * <b>Lifetime.</b> One provider is created, attached to the display's screen once
 * and then reloaded in place on every theme switch. Reloading is what carries a
 * switch: {@code gtk_css_provider_load_from_data} signals
 * {@code GtkStyleProvider::changed} and GTK restyles by itself, so there is nothing
 * to detach and no widget to walk. Unlike SWT we deliberately do not
 * {@code g_object_unref} after attaching, because the pointer stays in use for the
 * life of the process.
 * <p>
 * Every method must be called on the user interface thread.
 */
final class GtkStyleProvider {

	/** Version independent bindings: the provider itself and the screen lookup. */
	private static final List<String> COMMON = List.of("org.eclipse.swt.internal.gtk.GTK",
			"org.eclipse.swt.internal.gtk.GDK");

	/** Where SWT keeps the GTK 3 entry points, newest layout first. */
	private static final List<String> GTK3 = List.of("org.eclipse.swt.internal.gtk3.GTK3",
			"org.eclipse.swt.internal.gtk.GTK3");

	/** Where SWT keeps the GTK 4 entry points, newest layout first. */
	private static final List<String> GTK4 = List.of("org.eclipse.swt.internal.gtk4.GTK4",
			"org.eclipse.swt.internal.gtk.GTK4");

	/** The value GTK_STYLE_PROVIDER_PRIORITY_USER has had since GTK 3.0. */
	private static final int PRIORITY_USER = 800;

	/**
	 * SWT's own loader, because the internal GTK packages are exported {@code
	 * x-internal} from a fragment that exists on GTK alone. Asking the loader that
	 * owns them works whatever the manifest says and whatever the layout is.
	 */
	private final ClassLoader swt = Display.class.getClassLoader();

	private final List<Class<?>> bindings;

	private long provider;

	GtkStyleProvider() {
		boolean gtk4 = Boolean.TRUE.equals(constant(candidates(false), "GTK4"));
		bindings = candidates(gtk4);
	}

	/** Whether the sheet is live, so that clearing it can be skipped when it is not. */
	boolean isAttached() {
		return provider != 0;
	}

	/**
	 * Makes the given stylesheet the content of the provider, attaching the provider
	 * first if this is the first call. An empty string is how a switch to a theme that
	 * is not ours gives the desktop theme back.
	 */
	void write(String stylesheet) throws ReflectiveOperationException {
		if (provider == 0) {
			provider = attachedProvider();
		}
		// SWT hands GTK NUL terminated bytes and a length of -1, and so do we. The GTK 3
		// binding takes a fourth argument for the GError out parameter GTK 4 dropped.
		byte[] data = (stylesheet + '\0').getBytes(StandardCharsets.UTF_8);
		Method load = method("gtk_css_provider_load_from_data");
		Object[] arguments = switch (load.getParameterCount()) {
		case 3 -> new Object[] { provider, data, -1L };
		case 4 -> new Object[] { provider, data, -1L, null };
		default -> throw new NoSuchMethodException(
				"gtk_css_provider_load_from_data takes " + load.getParameterCount() + " arguments");
		};
		load.invoke(null, arguments);
	}

	private long attachedProvider() throws ReflectiveOperationException {
		// One of the two pairs is present, never both: the GTK 3 binding attaches to a
		// GdkScreen and the GTK 4 binding to a GdkDisplay, which replaced it.
		Method attach = optional("gtk_style_context_add_provider_for_screen")
				.or(() -> optional("gtk_style_context_add_provider_for_display"))
				.orElseThrow(() -> new NoSuchMethodException(
						"SWT's GTK bindings declare no way to attach a style provider"));
		Method root = attach.getName().endsWith("for_screen") ? method("gdk_screen_get_default")
				: method("gdk_display_get_default");

		long target = (long) root.invoke(null);
		if (target == 0) {
			throw new IllegalStateException(root.getName() + " returned NULL");
		}
		long created = (long) method("gtk_css_provider_new").invoke(null);
		if (created == 0) {
			throw new IllegalStateException("gtk_css_provider_new returned NULL");
		}
		attach.invoke(null, target, created, priority());
		return created;
	}

	private int priority() {
		Object declared = constant(bindings, "GTK_STYLE_PROVIDER_PRIORITY_USER");
		return declared instanceof Integer value ? value : PRIORITY_USER;
	}

	private Method method(String name) throws NoSuchMethodException {
		return optional(name).orElseThrow(() -> new NoSuchMethodException("SWT's GTK bindings declare no " + name));
	}

	private Optional<Method> optional(String name) {
		for (Class<?> binding : bindings) {
			for (Method candidate : binding.getMethods()) {
				if (candidate.getName().equals(name)) {
					return Optional.of(candidate);
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Reading a constant is best effort: a missing one falls back to the value GTK has
	 * had since 3.0, while a missing method has to be reported.
	 */
	private static Object constant(List<Class<?>> bindings, String name) {
		for (Class<?> binding : bindings) {
			try {
				Field field = binding.getField(name);
				return field.get(null);
			} catch (ReflectiveOperationException | RuntimeException e) {
				continue;
			}
		}
		return null;
	}

	/**
	 * The common bindings plus the ones for the GTK actually running. Both the GTK 3
	 * and the GTK 4 class are on the classpath at once, and each declares a
	 * {@code gtk_css_provider_load_from_data} of its own, so which one is asked has to
	 * follow {@code GTK.GTK4} rather than whichever loads first.
	 */
	private List<Class<?>> candidates(boolean gtk4) {
		List<Class<?>> classes = new ArrayList<>();
		for (String name : COMMON) {
			binding(name).ifPresent(classes::add);
		}
		for (String name : gtk4 ? GTK4 : GTK3) {
			binding(name).ifPresent(classes::add);
		}
		return classes;
	}

	private Optional<Class<?>> binding(String name) {
		try {
			return Optional.of(Class.forName(name, false, swt));
		} catch (ClassNotFoundException | LinkageError e) {
			return Optional.empty();
		}
	}
}
