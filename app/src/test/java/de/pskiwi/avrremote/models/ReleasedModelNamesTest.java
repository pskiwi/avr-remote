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
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

/**
 * Jeder Modellname, der je in einem Release stand, steht so in den
 * Einstellungen eines Nutzers - samt "(experimental)". Verschwindet seine
 * Klasse oder wird sie umbenannt, landet dieser Nutzer still bei
 * {@link AVRGeneric} und verliert alle Fähigkeiten seines Geräts.
 * {@code ModelConfiguratorTest} sieht das nicht: dort verschwinden Liste und
 * Klasse gemeinsam.
 *
 * Die Liste wächst nur. Neue Einträge von {@code @array/modelNames} kommen
 * mit dem Release dazu, das sie ausliefert (RELEASE.md, Schritt 3). Wer ein
 * Modell bewusst streicht, braucht dafür eine Migration, nicht eine kürzere
 * Liste.
 */
public final class ReleasedModelNamesTest {

	/**
	 * @array/modelNames ohne AVR-Generic, unverändert seit v1.7.0 - der
	 * Stand vor den generierten Modellen.
	 */
	private static final String[] RELEASED = { "AV-7005", "AV-7701",
			"AV-8801", "AVC-A1HDA", "AVP-A1HDCI", "AVR-100", "AVR-990",
			"AVR-991", "AVR-1613", "AVR-1713", "AVR-1912", "AVR-1913",
			"AVR-2112", "AVR-2113", "AVR-2312", "AVR-2313", "AVR-3310",
			"AVR-3311", "AVR-3312", "AVR-3313", "AVR-3805", "AVR-3806",
			"AVR-3808", "AVR-4306", "AVR-4308", "AVR-4310", "AVR-4311",
			"AVR-4520", "AVR-4806", "AVR-4810", "AVR-5308", "AVR-5805",
			"AVR-E300", "AVR-E400", "AVR-X1000", "AVR-X2000", "AVR-X3000",
			"AVR-X4000", "DN-500AV", "NR-1504", "NR-1602", "NR-1603",
			"NR-1604", "SR-5006", "SR-5007", "SR-5008", "SR-6005", "SR-6006",
			"SR-6007", "SR-6008", "SR-7005", "SR-7007", "SR-7008",
			"ASD-51 (experimental)", "DNP-720AE", "M-CR603 (experimental)",
			"M-ER803 (experimental)", "NA-7004 (experimental)",
			"RCD-N7 (experimental)" };

	@Test
	public void everyReleasedNameStillHasItsModel() {
		for (String name : RELEASED) {
			assertNotEquals("\"" + name + "\" fällt auf AVRGeneric zurück",
					AVRGeneric.class, ModelConfigurator.createModel(name)
							.getClass());
		}
	}

	/**
	 * Vor der Modellsuche wird der gespeicherte Name an die heutige Liste
	 * angeglichen ({@link ModelConfigurator#currentListName}), und dieser
	 * Abgleich ist großzügiger als die Suche selbst: er übersieht auch
	 * Leerzeichen und Groß/Klein. Ein künftiger Eintrag, der wie ein alter
	 * Name aussieht, aber auf eine andere Klasse zeigt, würde Nutzer still
	 * umstellen - der Test oben sähe es nicht.
	 */
	@Test
	public void aligningAReleasedNameKeepsItsModel() throws Exception {
		final String[] list = ModelConfiguratorTest.readModelNames().toArray(
				new String[0]);
		for (String name : RELEASED) {
			assertEquals("\"" + name + "\" landet nach dem Angleichen woanders",
					ModelConfigurator.createModel(name).getClass(),
					ModelConfigurator.createModel(
							ModelConfigurator.currentListName(name, list))
							.getClass());
		}
	}

	/** Der Fallback selbst ist auch ein Listeneintrag und muss es bleiben. */
	@Test
	public void genericStaysGeneric() {
		assertEquals(AVRGeneric.class,
				ModelConfigurator.createModel("AVR-Generic").getClass());
	}
}
