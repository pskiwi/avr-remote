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
	 * @array/modelNames ohne AVR-Generic: bis "RCD-N7" der Stand von v1.7.0,
	 * danach die generierten Modelle aus v1.8.0.
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
			"RCD-N7 (experimental)", "AV7701-G (experimental)",
			"AV7702 (experimental)", "AV7702mkII (experimental)",
			"AV8801-G (experimental)", "AV8802 (experimental)",
			"AVC-3310 (experimental)", "AVR-990-G (experimental)",
			"AVR-3310-G (experimental)", "AVR-A1H (experimental)",
			"AVR-A110 (experimental)", "AVR-S640H (experimental)",
			"AVR-S650H (experimental)", "AVR-S740H (experimental)",
			"AVR-S750H (experimental)", "AVR-S940H (experimental)",
			"AVR-S950H (experimental)", "AVR-S960H (experimental)",
			"AVR-S970H (experimental)", "AVR-X1100W (experimental)",
			"AVR-X1200W (experimental)", "AVR-X1500H (experimental)",
			"AVR-X1600H (experimental)", "AVR-X2100W (experimental)",
			"AVR-X2200W (experimental)", "AVR-X2500H (experimental)",
			"AVR-X2600H (experimental)", "AVR-X2700H (experimental)",
			"AVR-X2800H (experimental)", "AVR-X3100W (experimental)",
			"AVR-X3200W (experimental)", "AVR-X3500H (experimental)",
			"AVR-X3600H (experimental)", "AVR-X3700H (experimental)",
			"AVR-X3800H (experimental)", "AVR-X4100W (experimental)",
			"AVR-X4200W (experimental)", "AVR-X4700H (experimental)",
			"AVR-X4800H (experimental)", "AVR-X5200W (experimental)",
			"AVR-X6700H (experimental)", "AVR-X7200W (experimental)",
			"DRA-800H (experimental)", "NR1504-G (experimental)",
			"NR1506 (experimental)", "NR1602-G (experimental)",
			"NR1603-G (experimental)", "NR1604-G (experimental)",
			"NR1605 (experimental)", "NR1606 (experimental)",
			"S700W (experimental)", "S900W (experimental)",
			"SR5006-G (experimental)", "SR5007-G (experimental)",
			"SR5008-G (experimental)", "SR5009 (experimental)",
			"SR5010 (experimental)", "SR6006-G (experimental)",
			"SR6007-G (experimental)", "SR6008-G (experimental)",
			"SR6009 (experimental)", "SR6010 (experimental)",
			"SR7007-G (experimental)", "SR7008-G (experimental)",
			"SR7009 (experimental)", "SR7010 (experimental)" };

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
