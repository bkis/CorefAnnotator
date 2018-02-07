package de.unistuttgart.ims.coref.annotator;

import java.util.Set;

import org.reflections.Reflections;

import de.unistuttgart.ims.coref.annotator.plugins.IOPlugin;
import de.unistuttgart.ims.coref.annotator.plugins.StylePlugin;

public class PluginManager {
	Set<Class<? extends IOPlugin>> ioPlugins;
	Set<Class<? extends StylePlugin>> stylePlugins;

	public void init() {
		Reflections reflections = new Reflections("de.unistuttgart.ims.coref.annotator.plugins");
		ioPlugins = reflections.getSubTypesOf(IOPlugin.class);
		stylePlugins = reflections.getSubTypesOf(StylePlugin.class);

	}

	public Set<Class<? extends IOPlugin>> getIOPlugins() {
		return ioPlugins;
	}

	public Set<Class<? extends StylePlugin>> getStylePlugins() {
		return stylePlugins;
	}

}