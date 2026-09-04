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
import java.util.Optional;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
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
 * Two entry points, because a theme is either already active when the IDE comes up
 * or is chosen later. {@link #earlyStartup()} handles the first and is also what
 * activates this bundle at all, since nothing else refers to it;
 * {@link #handleEvent(Event)} handles the second, through the {@code THEME_CHANGED}
 * event {@code IThemeEngine} documents for exactly this purpose.
 * <p>
 * Being an {@code org.eclipse.ui.startup} contribution also gives the layer an off
 * switch that costs no code and no preference page of its own: clearing this bundle
 * under Preferences > General > Startup and Shutdown stops it from ever running.
 * {@code -Dcom.vogella.eclipse.themes.gtk=false} does the same for a single launch.
 */
public final class GtkThemeStartup implements IStartup, EventHandler {

	/** The display data key under which the e4 engine parks its theme engine. */
	private static final String THEME_ENGINE = "org.eclipse.e4.ui.css.swt.theme";

	private static final String ENABLED = "com.vogella.eclipse.themes.gtk";

	private final ILog log = Platform.getLog(GtkThemeStartup.class);

	private GtkStyleProvider provider;

	private Display display;

	/**
	 * Set once the window system has answered that it cannot do this, so that a user
	 * who switches themes a few times does not collect one log entry per switch.
	 */
	private boolean unsupported;

	@Override
	public void earlyStartup() {
		if (!Boolean.parseBoolean(System.getProperty(ENABLED, "true"))) {
			return;
		}
		display = PlatformUI.getWorkbench().getDisplay();
		display.asyncExec(() -> {
			provider = new GtkStyleProvider();
			listenForThemeChanges();
			follow(activeThemeId());
		});
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
		String themeId = theme instanceof ITheme active ? active.getId() : null;
		if (display != null && !display.isDisposed()) {
			display.asyncExec(() -> follow(themeId));
		}
	}

	private void follow(String themeId) {
		if (unsupported || provider == null || display == null || display.isDisposed()) {
			return;
		}
		try {
			Optional<Palette> palette = themeId == null ? Optional.empty() : Palette.of(themeId);
			if (palette.isEmpty()) {
				// Not one of ours. Emptying the sheet hands the widgets back to the desktop
				// theme, which is the right answer for the platform themes and for any other
				// third party theme the user switches to.
				if (provider.isAttached()) {
					provider.write("");
					repaint();
				}
				return;
			}
			provider.write(GtkStyleSheet.resolve(palette.get()));
			repaint();
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
		Object engine = display.getData(THEME_ENGINE);
		if (!(engine instanceof IThemeEngine themeEngine)) {
			return null;
		}
		ITheme active = themeEngine.getActiveTheme();
		return active == null ? null : active.getId();
	}

	private void listenForThemeChanges() {
		Bundle bundle = FrameworkUtil.getBundle(GtkThemeStartup.class);
		BundleContext context = bundle == null ? null : bundle.getBundleContext();
		if (context == null) {
			log.warn("No bundle context, so the GTK stylesheet will not follow a theme switch");
			return;
		}
		Dictionary<String, Object> properties = new Hashtable<>();
		properties.put(EventConstants.EVENT_TOPIC, IThemeEngine.Events.THEME_CHANGED);
		context.registerService(EventHandler.class, this, properties);
	}
}
