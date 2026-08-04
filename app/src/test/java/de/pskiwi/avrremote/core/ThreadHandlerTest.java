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
package de.pskiwi.avrremote.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import de.pskiwi.avrremote.core.ResilentConnector.ThreadHandler;

/**
 * Sichert das Abräumen des Reconnect-Threads ab. Der Aufrufer ist
 * {@code stopConnector()}, und der läuft über
 * {@code ActiveHandler.contextResumed()} → {@code forceReconnect()} auf dem
 * UI-Thread. Alles, was hier wartet, wartet dort im {@code onResume} und zählt
 * auf das ANR-Budget.
 *
 * <p>
 * Der Thread, der abgeräumt wird, hängt typischerweise in
 * {@code ConnectionConfiguration.checkAddress()} → {@code InetAddress.isReachable()}
 * und mehreren Socket-Connects. Nichts davon reagiert auf {@code interrupt()} -
 * ein Warten auf sein Ende läuft also verlässlich in den Timeout und bringt
 * nichts ein. Genau dieser Thread wird hier nachgestellt.
 */
public final class ThreadHandlerTest {

	/** Ein Thread, der sich - wie ein Ping im Timeout - nicht unterbrechen lässt. */
	private static final class UninterruptibleRunner implements Runnable {

		public void run() {
			started.countDown();
			final long until = System.currentTimeMillis() + BLOCK_MILLIS;
			while (System.currentTimeMillis() < until) {
				try {
					Thread.sleep(BLOCK_MILLIS);
				} catch (InterruptedException x) {
					// genau das macht isReachable() auch: weitermachen
					interrupts.countDown();
				}
			}
		}

		private final CountDownLatch started = new CountDownLatch(1);
		private final CountDownLatch interrupts = new CountDownLatch(1);
		private static final long BLOCK_MILLIS = 5000;
	}

	@Test
	public void stopWaitsNotForAnUninterruptibleThread() throws Exception {
		final ThreadHandler handler = new ThreadHandler();
		final UninterruptibleRunner runner = new UninterruptibleRunner();
		handler.start(runner, 1);
		assertTrue("Thread nicht angelaufen",
				runner.started.await(5, TimeUnit.SECONDS));

		final long start = System.currentTimeMillis();
		handler.stop();
		final long elapsed = System.currentTimeMillis() - start;

		// Die Schwelle liegt zwischen "gar nicht warten" (~0ms) und dem
		// frueheren join(1000), damit ein Rueckfall auffliegt.
		assertTrue("stop() hat " + elapsed + "ms auf dem Aufrufer verbracht",
				elapsed < 500);
	}

	@Test
	public void stopInterruptsTheThread() throws Exception {
		final ThreadHandler handler = new ThreadHandler();
		final UninterruptibleRunner runner = new UninterruptibleRunner();
		handler.start(runner, 1);
		assertTrue("Thread nicht angelaufen",
				runner.started.await(5, TimeUnit.SECONDS));

		handler.stop();

		// Ohne das Warten darf das Signal nicht mit verloren gehen: ein Thread,
		// der im unterbrechbaren Reconnect-Delay parkt, muss weiterhin sofort
		// aufwachen und sich beenden.
		assertTrue("kein interrupt angekommen",
				runner.interrupts.await(5, TimeUnit.SECONDS));
	}

	@Test
	public void isDefinedIsFalseAfterStop() throws Exception {
		final ThreadHandler handler = new ThreadHandler();
		final UninterruptibleRunner runner = new UninterruptibleRunner();
		handler.start(runner, 1);
		assertTrue("Thread nicht angelaufen",
				runner.started.await(5, TimeUnit.SECONDS));
		assertTrue(handler.isDefined());

		handler.stop();

		assertFalse(handler.isDefined());
	}

	@Test
	public void isDefinedIsFalseWhenTheThreadDiedByItself() throws Exception {
		final ThreadHandler handler = new ThreadHandler();
		final CountDownLatch done = new CountDownLatch(1);
		handler.start(new Runnable() {
			public void run() {
				done.countDown();
			}
		}, 1);
		assertTrue("Thread nicht gelaufen", done.await(5, TimeUnit.SECONDS));

		// Der Thread ist zu Ende, ohne dass jemand stop() gerufen hat. Wuerde
		// isDefined() nur "!= null" pruefen, hielte reconfigure() den toten
		// Loop fuer lebendig und startete nie einen neuen.
		for (int i = 0; i < 50 && handler.isDefined(); i++) {
			Thread.sleep(10);
		}
		assertFalse("toter Thread gilt noch als definiert", handler.isDefined());
	}

	/**
	 * Die reale Sequenz aus {@code forceReconnect()}: stoppen und sofort neu
	 * starten. Der Handler muss danach den <em>neuen</em> Thread fuehren, sonst
	 * haelt reconfigure() den Loop fuer tot oder fuer lebendig, je nachdem.
	 */
	@Test
	public void startAfterStopTracksTheNewThread() throws Exception {
		final ThreadHandler handler = new ThreadHandler();
		final UninterruptibleRunner first = new UninterruptibleRunner();
		handler.start(first, 1);
		assertTrue(first.started.await(5, TimeUnit.SECONDS));
		handler.stop();
		assertFalse(handler.isDefined());

		final UninterruptibleRunner second = new UninterruptibleRunner();
		handler.start(second, 2);
		assertTrue(second.started.await(5, TimeUnit.SECONDS));

		// Der erste laeuft noch - er ignoriert den interrupt -, darf den
		// Handler aber nicht mehr belegen.
		assertTrue("erster Thread hat den interrupt nicht bekommen",
				first.interrupts.await(5, TimeUnit.SECONDS));
		assertTrue("neuer Thread nicht uebernommen", handler.isDefined());

		// isDefined() allein wuerde nicht unterscheiden, welchen der beiden der
		// Handler fuehrt - der erste lebt ja noch. Erst dass dieses stop() beim
		// zweiten ankommt, belegt es.
		handler.stop();
		assertTrue("stop() ging nicht an den zweiten Thread",
				second.interrupts.await(5, TimeUnit.SECONDS));
	}
}
