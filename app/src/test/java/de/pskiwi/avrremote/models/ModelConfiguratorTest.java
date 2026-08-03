/*
 * Copyright the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.pskiwi.avrremote.models;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Prüft die einzige Verbindung zwischen {@code @array/modelNames} in
 * {@code res/values/lists.xml} und den Klassen in {@code models/}: sie besteht
 * nur aus dem Klassennamen, den
 * {@link ModelConfigurator#createModel(String)} per Reflection auflöst. Kein
 * Compiler sieht, wenn eine der beiden Seiten umbenannt wird oder ein Eintrag
 * fehlt - der Anwender bekommt dann still {@link AVRGeneric} und verliert alle
 * Fähigkeiten seines Geräts.
 */
public final class ModelConfiguratorTest {

	@Test
	public void everyModelNameResolvesToItsOwnClass() throws Exception {
		for (String modelName : readModelNames()) {
			assertEquals("Auswahl \"" + modelName
					+ "\" landet nicht in ihrer Modellklasse",
					expectedClassName(modelName), ModelConfigurator
							.createModel(modelName).getClass().getSimpleName());
		}
	}

	/** Gegenrichtung: eine Modellklasse, die niemand auswählen kann. */
	@Test
	public void everyModelClassIsSelectable() throws Exception {
		final Set<String> fromList = new TreeSet<String>();
		for (String modelName : readModelNames()) {
			fromList.add(expectedClassName(modelName));
		}

		final Set<String> onDisk = new TreeSet<String>();
		for (File f : modelSources()) {
			final String name = f.getName();
			if (!name.endsWith(".java")) {
				continue;
			}
			final String simpleName = name.substring(0, name.length()
					- ".java".length());
			final Class<?> c = Class.forName("de.pskiwi.avrremote.models."
					+ simpleName);
			// Interface und die beiden abstrakten Basisklassen fallen hier
			// raus, ebenso die Infrastruktur (ModelArea, DynamicEQ*, ...)
			if (IAVRModel.class.isAssignableFrom(c)
					&& !Modifier.isAbstract(c.getModifiers())) {
				onDisk.add(simpleName);
			}
		}

		assertEquals("modelNames in lists.xml und models/ sind auseinander"
				+ " gelaufen", onDisk, fromList);
	}

	/**
	 * Hält fest, warum die Tests oben auf der Klasse bestehen und nicht nur
	 * darauf, dass irgendein Modell herauskommt: der Fehlerfall ist stumm.
	 */
	@Test
	public void unknownModelFallsBackToGenericWithoutFailing() {
		assertEquals(AVRGeneric.class,
				ModelConfigurator.createModel("Kein-Solches-Modell").getClass());
	}

	/** "ASD-51 (experimental)" -> "ASD51" */
	private static String expectedClassName(String modelName) {
		final int p = modelName.indexOf('(');
		final String withoutRemark = p == -1 ? modelName : modelName.substring(
				0, p);
		return withoutRemark.replace("-", "").trim();
	}

	private static List<String> readModelNames() throws Exception {
		final Document doc = DocumentBuilderFactory.newInstance()
				.newDocumentBuilder()
				.parse(moduleFile("src/main/res/values/lists.xml"));
		final NodeList arrays = doc.getElementsByTagName("string-array");
		for (int i = 0; i < arrays.getLength(); i++) {
			final Element array = (Element) arrays.item(i);
			if ("modelNames".equals(array.getAttribute("name"))) {
				final NodeList items = array.getElementsByTagName("item");
				final List<String> names = new ArrayList<String>();
				for (int j = 0; j < items.getLength(); j++) {
					names.add(items.item(j).getTextContent().trim());
				}
				assertTrue("modelNames ist leer", !names.isEmpty());
				// Ohne diese Prüfung wäre ein doppelter Eintrag grün: unten
				// steht ein Set, und beide Kopien lösen ja auf
				assertEquals("doppelter Eintrag in modelNames",
						new TreeSet<String>(names).size(), names.size());
				return names;
			}
		}
		throw new AssertionError("string-array modelNames fehlt in lists.xml");
	}

	private static File[] modelSources() {
		final File[] files = moduleFile(
				"src/main/java/de/pskiwi/avrremote/models").listFiles();
		assertTrue("models/ ist nicht lesbar", files != null);
		return files;
	}

	/** Der Test-Runner startet je nach Aufruf im Modul- oder im Wurzelordner. */
	private static File moduleFile(String relativePath) {
		File f = new File(relativePath);
		if (!f.exists()) {
			f = new File("app", relativePath);
		}
		assertTrue(relativePath + " nicht gefunden (cwd "
				+ new File(".").getAbsolutePath() + ")", f.exists());
		return f;
	}
}
