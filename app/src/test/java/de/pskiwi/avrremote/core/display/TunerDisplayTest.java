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
package de.pskiwi.avrremote.core.display;

import static org.junit.Assert.assertEquals;

import java.text.DecimalFormatSymbols;

import org.junit.Test;

import de.pskiwi.avrremote.core.InData;

/**
 * Die Frequenzanzeige des analogen Tuners. Das Format steht im DENON AVR
 * control protocol Ver. 5.2 (AVR-3808CI, Juli 2007):
 *
 * <pre>
 * TF  AN****** (6 digits)  --- ****.** kHz at AM band  (&gt;050000 is AM.)
 *                              ****.** MHz at FM band  (&lt;050000 is FM.)
 * </pre>
 *
 * Sechs Ziffern, hundertstelgenau, Bandgrenze bei 050000. Beide Bedingungen
 * sind strikt notiert, der Grenzwert selbst ist im Papier also gar nicht
 * vergeben - siehe {@link #theBoundaryValueFallsToAM()}.
 *
 * Nur die Umrechnung ist hier abgedeckt. Der Rest von TunerDisplay - Vorwahlen,
 * HD-Radio, DAB, die Statuszeilen - hat weiterhin keine Tests.
 */
public final class TunerDisplayTest {

	@Test
	public void frequencyBelowTheBoundaryIsFM() {
		assertEquals("FM : 104" + sep() + "30 MHz", frequency("10430"));
	}

	@Test
	public void frequencyAboveTheBoundaryIsAM() {
		assertEquals("AM : 1050" + sep() + "00 kHz", frequency("105000"));
	}

	/**
	 * Der Grenzwert 050000 ist im Papier unbestimmt - beide Bedingungen sind
	 * strikt, "&gt;050000 is AM" und "&lt;050000 is FM", er selbst gehört zu
	 * keinem der beiden. Hier fällt er auf AM. Das ist eine Setzung, keine
	 * Vorgabe: ein echter Tuner steht nie dort, UKW endet bei 10800 und MW
	 * beginnt bei 52200. Der Fall steht hier, damit die Setzung nicht
	 * unbemerkt hin und her wandert.
	 */
	@Test
	public void theBoundaryValueFallsToAM() {
		assertEquals("AM : 500" + sep() + "00 kHz", frequency("050000"));
	}

	/**
	 * "CMP" quittiert das Ende eines Suchlaufs und ist keine Frequenz. Es darf
	 * eine bereits angezeigte Frequenz nicht löschen - deshalb kommt hier erst
	 * eine echte, dann CMP.
	 */
	@Test
	public void completionMessageLeavesTheFrequencyAlone() {
		final TunerDisplay.TunerFrequency f = tuner();
		f.update(new InData("10430"));
		final String before = f.getFrequency();

		f.update(new InData("CMP"));

		assertEquals("FM : 104" + sep() + "30 MHz", before);
		assertEquals(before, f.getFrequency());
	}

	/**
	 * Baut einen UKW-Tuner und schiebt ihm eine TF-Nutzlast unter.
	 * ModelConfigurator und ISender werden dabei nicht angefasst, und der
	 * Anzeige-Zuhörer steht auf dem Null-Objekt.
	 */
	private static String frequency(String payload) {
		final TunerDisplay.TunerFrequency f = tuner();
		f.update(new InData(payload));
		return f.getFrequency();
	}

	private static TunerDisplay.TunerFrequency tuner() {
		return TunerDisplay.createFM(null, null).new TunerFrequency();
	}

	/** String.format richtet sich nach der Standardsprache, der Test auch. */
	private static char sep() {
		return new DecimalFormatSymbols().getDecimalSeparator();
	}
}
