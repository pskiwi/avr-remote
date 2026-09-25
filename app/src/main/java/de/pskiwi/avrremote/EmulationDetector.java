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
package de.pskiwi.avrremote;

import android.os.Build;

public final class EmulationDetector {

	public static boolean isEmulator() {
		return EMULATOR;
	}

	// Null-sicher, weil im Stub-android.jar eines JVM-Tests jeder Feldzugriff
	// null liefert. Ohne die Wachen stirbt hier der <clinit> mit einer NPE,
	// und mit ihm jeder Aufrufer - InData.toDebugString() fragt das ab, und
	// das wiederum steht in Connector.Receiver.run() auf dem Empfangspfad:
	// der Receiver-Thread ging so beim ersten empfangenen Byte still verloren.
	// Auf einem Gerät sind beide Felder gesetzt, dort ändert sich nichts.
	private final static boolean EMULATOR = Build.PRODUCT != null
			&& Build.PRODUCT.toUpperCase().contains("SDK")
			&& "generic".equals(Build.DEVICE);

}
