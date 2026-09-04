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

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.e4.ui.css.swt.theme.ITheme;
import org.eclipse.e4.ui.css.swt.theme.IThemeEngine;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.PlatformUI;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

/**
 * Keeps the GTK stylesheet in step with the active theme.
 * <p>
 * The whole layer is invisible when it works and invisible when it does not, so
 * every path through this class ends in either a stylesheet or a log entry. There is
 * no third outcome, and there used to be: a lookup that came back empty simply
 * returned, which is indistinguishable from the bundle not being installed at all.
 * <p>
 * <b>Finding out which theme is active.</b> Three sources, in order, because each
 * one can be absent on its own. The {@code IThemeEngine} parked in the display's data
 * is the direct answer but is only there once something has asked
 * {@code ThemeEngineManager} for it; the workbench service is the same object by
 * another route; and the {@code themeid} instance preference is what
 * {@code ThemeEngine.setTheme} writes and flushes, so it survives anything. The
 * fallbacks matter most in the ordinary case: when the theme was already active at
 * startup, no theme change is ever broadcast, and a failure to read it here would
 * leave the sheet unwritten for the whole session.
 * <p>
 * <b>Noticing a switch.</b> Two triggers, for the same reason. The
 * {@code THEME_CHANGED} event is the one {@code IThemeEngine} documents, but
 * {@code ThemeEngine.sendThemeChangeEvent} gives up quietly when no
 * {@code EventAdmin} is registered, which is a thing that happens in a launch
 * configuration that did not include one. The {@code themeid} preference is written
 * on the same code path and cannot be opted out of. Whichever arrives first wins and
 * the other is a no-op, because the active id is remembered.
 * <p>
 * <b>Why this sits in the common bundle.</b> It was a GTK only bundle of its own at
 * first, gated by {@code Eclipse-PlatformFilter: (osgi.ws=gtk)}, which is the tidier
 * shape and the wrong one. A bundle nobody has ever heard of is a bundle that is not
 * in the workspace after a checkout, not in an Eclipse Application launch
 * configuration set to "plug-ins selected below", and reachable from an installed
 * theme only through an optional feature include that p2 is free to drop. All three
 * failure modes look identical from the outside: the theme applies and these widgets
 * do not change. Every theme already requires
 * {@code com.vogella.eclipse.themes.common} and imports its stylesheets, so a theme
 * that renders at all proves this bundle is resolved and active. The window system is
 * a runtime question here instead, checked in {@link #earlyStartup()}.
 * <p>
 * Being an {@code org.eclipse.ui.startup} contribution also gives the layer an off
 * switch that costs no code and no preference page of its own: clearing this bundle
 * under Preferences > General > Startup and Shutdown stops it from ever running.
 * {@code -Dcom.vogella.eclipse.themes.gtk=false} does the same for a single launch.
 */
public final class GtkThemeStartup implements IStartup, EventHandler {

	/** The display data key under which the e4 engine parks its theme engine. */
	private static final String THEME_ENGINE = "org.eclipse.e4.ui.css.swt.theme";

	/** The instance preference {@code ThemeEngine.setTheme} writes and flushes. */
	private static final String THEME_ID_PREFERENCE = "themeid";

	private static final String ENABLED = "com.vogella.eclipse.themes.gtk";

	/** What every theme id of ours starts with, so a missing palette can be told
	 * apart from a theme that was never meant to have one. */
	private static final String OUR_THEMES = "com.vogella.eclipse.themes.";

	private final ILog log = Platform.getLog(GtkThemeStartup.class);

	private GtkStyleProvider provider;

	private Display display;

	/** The id the current sheet was resolved from, so two triggers do the work once. */
	private String applied;

	/**
	 * Set once the window system has answered that it cannot do this, so that a user
	 * who switches themes a few times does not collect one log entry per switch.
	 */
	private boolean unsupported;

	@Override
	public void earlyStartup() {
		// This lives in the bundle every theme already requires rather than in a GTK
		// only bundle of its own, so the window system is a runtime question rather than
		// an Eclipse-PlatformFilter. See the class comment.
		if (!Platform.WS_GTK.equals(Platform.getWS())) {
			return;
		}
		if (!Boolean.parseBoolean(System.getProperty(ENABLED, "true"))) {
			return;
		}
		display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(this::start);
	}

	private void start() {
		try {
			// Constructing this loads SWT's GTK bindings, so it can fail the same way
			// writing the sheet can, and outside a try it would surface as the workbench's
			// unhandled event loop exception dialog rather than as a log entry.
			provider = new GtkStyleProvider();
		} catch (RuntimeException | LinkageError e) {
			unsupported = true;
			log.warn("No GTK bindings, so the widgets GTK owns keep the desktop theme's colours", e);
			return;
		}
		listenForThemeChanges();
		follow(activeThemeId());
	}

	/**
	 * {@code ThemeEngine.setTheme} sends this synchronously and only then re-applies
	 * the stylesheets, so this runs in the middle of a theme switch. Nothing here
	 * reads anything the engine is still writing, the palette comes out of this
	 * bundle, but posting the work keeps a failure of ours off the engine's stack.
	 */
	@Override
	public void handleEvent(Event event) {
		Object theme = event.getProperty(IThemeEngine.Events.THEME);
		post(theme instanceof ITheme active ? active.getId() : null);
	}

	private void post(String themeId) {
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> follow(themeId));
		}
	}

	private void follow(String themeId) {
		if (unsupported || provider == null || display == null || display.isDisposed()) {
			return;
		}
		if (Objects.equals(themeId, applied)) {
			return;
		}
		try {
			Optional<Palette> palette = themeId == null ? Optional.empty() : Palette.of(themeId);
			if (palette.isEmpty()) {
				// Either a theme that is not ours, which is the common case and correct, or
				// one of ours whose palette did not ship. Only the second is worth a warning,
				// and the bundle prefix is the only thing that tells them apart.
				if (themeId != null && themeId.startsWith(OUR_THEMES)) {
					log.warn("No GTK palette for " + themeId + ", so the widgets GTK owns keep the desktop"
							+ " theme's colours. Expected palettes/" + themeId + ".properties in this bundle.");
				}
				applied = themeId;
				if (provider.isAttached()) {
					provider.write("");
					repaint();
					log.info("GTK stylesheet cleared for " + themeId + ", the desktop theme paints these widgets again");
				}
				return;
			}
			provider.write(GtkStyleSheet.resolve(palette.get()));
			repaint();
			applied = themeId;
			log.info("GTK stylesheet applied for " + themeId);
		} catch (Exception | LinkageError e) {
			unsupported = true;
			log.warn("Could not apply the GTK stylesheet for " + themeId
					+ "; the widgets GTK owns keep the desktop theme's colours", e);
		}
	}

	/**
	 * Repaints every control in every shell, one control at a time.
	 * <p>
	 * GTK does not queue a redraw when a provider on the screen changes, so without
	 * this the new colours appear only on widgets created afterwards and the IDE looks
	 * unaffected until something else happens to repaint. Measured against a live SWT
	 * {@code Tree}: writing the sheet alone left the selected row on the desktop
	 * accent through four captures, and it took the palette colour on the first
	 * capture after the tree was redrawn.
	 * <p>
	 * A shell wide {@code redraw(0, 0, width, height, true)} is not enough and was
	 * measured not to be. A {@code Tree} draws its rows into a {@code GdkWindow} of
	 * its own, which is not part of the region the shell invalidates, so the walk has
	 * to reach every control and ask each one itself. That is also why this cannot be
	 * left to the e4 engine: {@code ThemeEngine.setTheme} sends {@code THEME_CHANGED}
	 * before it re-applies, so its own repaint is already over by the time the sheet
	 * is written.
	 */
	private void repaint() {
		for (Shell shell : display.getShells()) {
			if (shell.isDisposed()) {
				continue;
			}
			repaint(shell);
			shell.update();
		}
	}

	private static void repaint(Control control) {
		control.redraw();
		if (control instanceof Composite composite) {
			for (Control child : composite.getChildren()) {
				repaint(child);
			}
		}
	}

	private String activeThemeId() {
		if (display.getData(THEME_ENGINE) instanceof IThemeEngine engine) {
			String themeId = activeThemeId(engine);
			if (themeId != null) {
				return themeId;
			}
		}
		IThemeEngine service = PlatformUI.getWorkbench().getService(IThemeEngine.class);
		if (service != null) {
			String themeId = activeThemeId(service);
			if (themeId != null) {
				return themeId;
			}
		}
		String themeId = preferences().get(THEME_ID_PREFERENCE, null);
		if (themeId == null) {
			log.warn("No theme engine and no '" + THEME_ID_PREFERENCE + "' preference, so the active theme is"
					+ " unknown and the widgets GTK owns keep the desktop theme's colours");
		}
		return themeId;
	}

	private static String activeThemeId(IThemeEngine engine) {
		ITheme active = engine.getActiveTheme();
		return active == null ? null : active.getId();
	}

	private static IEclipsePreferences preferences() {
		return InstanceScope.INSTANCE.getNode(THEME_ENGINE);
	}

	private void listenForThemeChanges() {
		// The preference first: it is the trigger that cannot be missing, so registering
		// it before the optional one means a failure below still leaves a working layer.
		preferences().addPreferenceChangeListener(event -> {
			if (THEME_ID_PREFERENCE.equals(event.getKey())) {
				post(event.getNewValue() instanceof String themeId ? themeId : null);
			}
		});

		Bundle bundle = FrameworkUtil.getBundle(GtkThemeStartup.class);
		BundleContext context = bundle == null ? null : bundle.getBundleContext();
		if (context == null) {
			log.warn("No bundle context, so a theme switch is only noticed through the '" + THEME_ID_PREFERENCE
					+ "' preference");
			return;
		}
		Dictionary<String, Object> properties = new Hashtable<>();
		properties.put(EventConstants.EVENT_TOPIC, IThemeEngine.Events.THEME_CHANGED);
		context.registerService(EventHandler.class, this, properties);
	}
}
