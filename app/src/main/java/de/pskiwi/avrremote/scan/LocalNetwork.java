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
package de.pskiwi.avrremote.scan;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import de.pskiwi.avrremote.AVRSettings;
import de.pskiwi.avrremote.EmulationDetector;
import de.pskiwi.avrremote.log.Logger;

/**
 * Einzige Quelle für den Zustand des lokalen Netzes: ob ein WLAN da ist, welches
 * {@link Network} dazu gehört und welche IPv4-Adresse das Gerät darin hat.
 *
 * Löst WiFiInfo und IFConfig ab. WiFiInfo hing an getDhcpInfo() und
 * getAllNetworks() - beide seit API 31 deprecated -, IFConfig rief als Notnagel
 * "ifconfig" über Runtime.exec() auf, was auf keiner unterstützten
 * Android-Version mehr etwas liefert. Beides beantwortet LinkProperties direkt,
 * und zwar auch außerhalb von DHCP, wo getDhcpInfo() nur Nullen zurückgab.
 *
 * Der zweite Grund für die Klasse: unter Local Network Protection (Pflicht ab
 * Android 17) muss jeder Socket ins lokale Netz an genau dieses Network gebunden
 * werden, nicht nur der Suchlauf. {@link #bind(Socket)},
 * {@link #bind(DatagramSocket)} und {@link #openConnection(URL)} sind die Quelle
 * dafür.
 */
public final class LocalNetwork {

	/** Wechsel des WLAN-Zustands, gemeldet auf dem Main-Thread. */
	public interface IWiFiListener {
		void wifiChanged(boolean connected);
	}

	public LocalNetwork(Context ctx) {
		this.connectivity = (ConnectivityManager) ctx
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		appContext = ctx.getApplicationContext();
	}

	/**
	 * Einmalig beim Start registrieren.
	 *
	 * Bewusst ohne addCapability(NET_CAPABILITY_INTERNET): NetworkRequest.Builder
	 * setzt per Default nur NOT_RESTRICTED/TRUSTED/NOT_VPN, und
	 * registerNetworkCallback meldet alle passenden Netze, nicht nur das
	 * Default-Netz. Damit bleibt genau die Eigenschaft erhalten, die der alte
	 * Zwei-Schritt aus getActiveNetwork() + getAllNetworks() mühsam nachgebildet
	 * hat: ein WLAN ohne Internet - Router mit totem WAN, Captive Portal, bewusst
	 * isoliertes AV-Netz - zählt neben aktivem Mobilfunk als verbunden. Der
	 * Receiver ist dann da, auch wenn das WLAN nicht das Default-Netz ist.
	 *
	 * Der Listener läuft über den Main-Handler, weil der abgelöste
	 * BroadcastReceiver in onReceive() auch auf dem Main-Thread lief und
	 * EnableManager.setStatus() ein unsynchronisiertes read-modify-write ist. Die
	 * Handler-Überladung von registerNetworkCallback() wäre der direktere Weg,
	 * ist aber erst API 26 und damit oberhalb von minSdk 24 - handler.post() im
	 * Callback leistet dasselbe ohne Versionsweiche.
	 *
	 * Ein bereits verbundenes WLAN meldet registerNetworkCallback von selbst nach,
	 * für "gar kein WLAN" gibt es dagegen keine Meldung: onLost kommt nur für ein
	 * Netz, das vorher verfügbar war. Der abgelöste BroadcastReceiver bekam
	 * NETWORK_STATE_CHANGED_ACTION sticky zugestellt und hat StatusFlag.WLAN
	 * deshalb immer sofort definiert. Ohne das Nachziehen unten bliebe das Flag
	 * beim Start ohne WLAN undefiniert - und ConfigurationAssistant.checkStatus
	 * zeigt "WLAN nicht aktiv" genau dann nicht an, wenn es zutrifft.
	 *
	 * Verzögert, weil die Nachmeldung über einen Binder-Thread kommt und synchron
	 * nicht zu haben ist: was nach SEED_DELAY nicht gemeldet wurde, gibt es auch
	 * nicht. Kommt sie später doch, korrigiert sie das Flag - die umgekehrte
	 * Richtung darf es nicht geben, ein gemeldetes WLAN nachträglich als "kein
	 * WLAN" zu überschreiben würde den Dialog für den falschen Fall aufmachen.
	 */
	public void register(Handler handler, IWiFiListener listener) {
		this.handler = handler;
		this.listener = listener;
		final NetworkRequest request = new NetworkRequest.Builder()
				.addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
		connectivity.registerNetworkCallback(request, callback);
		handler.postDelayed(new Runnable() {
			public void run() {
				notifyListenerIfNothingReported();
			}
		}, SEED_DELAY);
	}

	/** Ist ein WLAN mit brauchbarer IPv4-Adresse da ? */
	public boolean isConnected() {
		return boundNetwork != null && getIPv4() != null;
	}

	/** Das WLAN, an das Sockets zu binden sind, oder null. */
	public Network getNetwork() {
		return boundNetwork;
	}

	/**
	 * Socket an das WLAN binden, vor dem connect(). Ohne Bindung nimmt der
	 * Verbindungsaufbau das Default-Netz, und das ist neben aktivem Mobilfunk
	 * nicht das WLAN - der Receiver ist dann ueber Mobilfunk zu suchen und die
	 * Verbindung scheitert. Genau das Szenario, das die Netzerkennung hier
	 * muehsam als "verbunden" erkennt.
	 *
	 * Ist kein WLAN bekannt, entscheidet {@link #mayConnect()}: zeigt die
	 * Default-Route in reinen Mobilfunk, gibt es ohne "Mobilfunk nutzen" keine
	 * ungebundene Verbindung mehr, sondern eine IOException.
	 */
	public static void bind(Socket socket) throws IOException {
		final Network n = boundNetwork;
		if (n != null) {
			n.bindSocket(socket);
		} else {
			checkUnboundAllowed();
		}
	}

	/** Wie {@link #bind(Socket)}, fuer den SSDP-Suchlauf. */
	public static void bind(DatagramSocket socket) throws IOException {
		final Network n = boundNetwork;
		if (n != null) {
			n.bindSocket(socket);
		} else {
			checkUnboundAllowed();
		}
	}

	/** Wie {@link #bind(Socket)}, fuer das HTTP-Scraping. */
	public static URLConnection openConnection(URL url) throws IOException {
		final Network n = boundNetwork;
		if (n == null) {
			checkUnboundAllowed();
			return url.openConnection();
		}
		return n.openConnection(url);
	}

	/**
	 * Darf ueberhaupt ein Socket aufgebaut werden ? Mit gebundenem WLAN immer,
	 * sonst entscheidet {@link #mayConnectUnbound()}.
	 *
	 * Geprueft wird {@link #boundNetwork} und nicht StatusFlag.WLAN: das Flag
	 * laeuft ueber einen Handler-Post und kann dem Feld hinterherhinken, und ein
	 * Connect, der geklappt haette, darf nicht an einem veralteten Wert
	 * scheitern.
	 */
	public static boolean mayConnect() {
		return boundNetwork != null || mayConnectUnbound();
	}

	private static void checkUnboundAllowed() throws IOException {
		if (!mayConnectUnbound()) {
			throw new IOException("mobile network only, disabled by setting");
		}
	}

	/**
	 * Ohne Bindung geht der Socket dorthin, wo die Default-Route zeigt. Nur ein
	 * Fall davon kann nachweislich nicht ankommen: reiner Mobilfunk - eine
	 * lokale Adresse ist von dort nicht erreichbar, und jede Reconnect-Runde
	 * zahlt trotzdem den vollen Connect-Timeout. Genau der bleibt ab Werk aus.
	 *
	 * Alles andere ist erlaubt, und das ist keine Grosszuegigkeit, sondern
	 * Notwendigkeit: {@link #register} fragt nur TRANSPORT_WIFI ab, also ist
	 * {@link #boundNetwork} auch dann null, wenn das lokale Netz per Ethernet
	 * am Gerät hängt - Dongle am Tablet, DeX, ein Android-Fernseher. Vor dieser
	 * Sperre hat der ungebundene Socket dort funktioniert, und ohne die
	 * Einschraenkung auf Mobilfunk waere der Receiver dauerhaft unerreichbar,
	 * ohne dass ein Dialog sagen koennte warum. Zwei weitere Faelle kommen
	 * gratis mit: ein VPN, das den Receiver zu Hause durchaus erreicht, und das
	 * WLAN, das der Callback beim Start noch nicht gemeldet hat.
	 */
	private static boolean mayConnectUnbound() {
		if (appContext == null) {
			// JVM-Test: es gibt keine Preferences und keinen Application-Context,
			// der danach fragen koennte. Offen lassen, sonst kommt
			// HTTPSupportTest nicht mehr an seinen eigenen Socket.
			return true;
		}
		if (EmulationDetector.isEmulator()) {
			// wie in ConfigurationAssistant.checkStatus und AVRScanner.scan: im
			// Emulator taugt die Netzerkennung nicht als Grundlage.
			return true;
		}
		if (!isCellularOnly()) {
			return true;
		}
		return AVRSettings.isUseMobileNetwork(appContext);
	}

	/** Zeigt die Default-Route in ein Netz, das nichts als Mobilfunk ist ? */
	private static boolean isCellularOnly() {
		final ConnectivityManager cm = (ConnectivityManager) appContext
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		final Network active = cm.getActiveNetwork();
		if (active == null) {
			// Gar kein Netz: der Connect scheitert sofort und ohne Timeout,
			// dagegen muss nichts schuetzen.
			return false;
		}
		final NetworkCapabilities caps = cm.getNetworkCapabilities(active);
		return caps != null
				&& caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
				&& !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
				&& !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
				&& !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
	}

	/** Eigene IPv4-Adresse samt Prefix-Länge, oder null. */
	public LinkAddress getIPv4() {
		final LinkProperties properties = linkProperties;
		if (properties == null) {
			return null;
		}
		for (LinkAddress address : properties.getLinkAddresses()) {
			if (address.getAddress() instanceof Inet4Address) {
				return address;
			}
		}
		return null;
	}

	/** "wlan0" und ähnlich, nur für das Log. */
	public String getInterfaceName() {
		final LinkProperties properties = linkProperties;
		return properties == null ? null : properties.getInterfaceName();
	}

	public String getErrorCause() {
		if (boundNetwork == null) {
			return "WiFi not connected";
		}
		if (getIPv4() == null) {
			return "no IPv4 address";
		}
		return "no error";
	}

	@Override
	public String toString() {
		final LinkAddress address = getIPv4();
		return "LocalNetwork [" + getInterfaceName() + "] "
				+ (address == null ? "no address" : address.getAddress()
						.getHostAddress() + "/" + address.getPrefixLength());
	}

	/**
	 * Mehrere gleichzeitige WLAN-Netze sind praktisch ausgeschlossen, deshalb
	 * gewinnt schlicht das zuletzt verfügbare, und onLost räumt nur auf, wenn es
	 * das gehaltene Netz trifft.
	 */
	private final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {

		@Override
		public void onAvailable(Network n) {
			boundNetwork = n;
			linkProperties = connectivity.getLinkProperties(n);
			notifyListener(true);
		}

		/**
		 * onAvailable kommt, bevor DHCP fertig ist - die LinkProperties tragen
		 * dann noch keine IPv4-Adresse, und genau die braucht der Suchlauf
		 * ({@link #getIPv4()}). Kommt sie erst hier nach, muss das gemeldet
		 * werden: sonst steht StatusFlag.WLAN auf true, während
		 * {@link #isConnected()} noch false ist, und ein Scan in diesem Fenster
		 * bricht mit "no IPv4 address" ab, ohne dass sich danach etwas rührt.
		 */
		@Override
		public void onLinkPropertiesChanged(Network n, LinkProperties lp) {
			if (n.equals(boundNetwork)) {
				final boolean hadAddress = getIPv4() != null;
				linkProperties = lp;
				if (!hadAddress && getIPv4() != null) {
					notifyListener(true);
				}
			}
		}

		@Override
		public void onLost(Network n) {
			if (n.equals(boundNetwork)) {
				boundNetwork = null;
				linkProperties = null;
				notifyListener(false);
			}
		}
	};

	/**
	 * Der Seed aus {@link #register}: meldet nur, wenn sonst noch niemand etwas
	 * gemeldet hat.
	 */
	private synchronized void notifyListenerIfNothingReported() {
		if (!reported) {
			notifyListener(false);
		}
	}

	/**
	 * Synchronisiert, und zwar einschließlich des post(): der Callback meldet
	 * aus einem Binder-Thread, der Seed aus dem Main-Thread, und es reicht
	 * nicht, nur das Merkmal unter einem Schloss zu führen. Sonst entscheidet
	 * der Seed "noch nichts gemeldet", onAvailable postet dazwischen sein "WLAN
	 * da", und das "kein WLAN" des Seeds reiht sich dahinter ein. StatusFlag.WLAN
	 * stünde dann auf false, obwohl das Netz da ist, und nichts käme nach, um
	 * das zu berichtigen: für ein bereits verfügbares Netz meldet sich der
	 * Callback kein zweites Mal. Unter dem Schloss postet, wer zuerst meldet,
	 * auch zuerst.
	 */
	private synchronized void notifyListener(final boolean connected) {
		reported = true;
		Logger.info("LocalNetwork: WiFi " + (connected ? "available" : "lost")
				+ " " + this);
		final Handler h = handler;
		final IWiFiListener l = listener;
		if (h == null || l == null) {
			return;
		}
		h.post(new Runnable() {
			public void run() {
				l.wifiChanged(connected);
			}
		});
	}

	private final ConnectivityManager connectivity;
	// Statisch, weil die Sockets an Stellen entstehen, die den Objektgraphen
	// nicht erreichen: Connector sitzt tief in core/ und wird vom Reconnect-Loop
	// gebaut, HTTPSupport und AVRTargetTester sind reine Utility-Klassen. Es gibt
	// genau eine LocalNetwork-Instanz (AVRApplication.onCreate) und damit genau
	// einen Schreiber. Bewusst nicht bindProcessToNetwork(): das wirkt auf alles
	// im Prozess, auch auf Verbindungen, die gar nicht ins lokale Netz gehen.
	// Auf dem Binder-Thread des Callbacks geschrieben, von jedem Thread gelesen.
	private static volatile Network boundNetwork;
	private volatile LinkProperties linkProperties;
	private volatile Handler handler;
	private volatile IWiFiListener listener;
	/**
	 * Hat schon einmal jemand etwas gemeldet ? Nur unter dem Monitor dieser
	 * Instanz angefasst, siehe {@link #notifyListener} und {@link #register}.
	 */
	private boolean reported;
	/**
	 * Drei Sekunden, nicht eine: die Nachmeldung eines bereits verbundenen
	 * Netzes kommt über einen Binder-Thread und hat keine zugesicherte Frist,
	 * während der Main-Thread beim Start gerade AVRRemote aufbaut. Zu früh
	 * gemeldet hiesse "kein WLAN" für ein vorhandenes, und der Assistent
	 * bekäme einen Grund, den falschen Dialog zu zeigen. Zu spät kostet
	 * nichts: gebraucht wird das Flag erst, wenn der Verbindungsversuch
	 * aufgegeben hat, und das dauert länger.
	 */
	private static final long SEED_DELAY = 3000;

	/**
	 * Fuer {@link #mayConnectUnbound()}. Statisch und volatile wie boundNetwork
	 * daneben: geschrieben wird im Konstruktor auf dem Main-Thread, gelesen vom
	 * Reconnect-Thread und von den HTTP-Threads.
	 */
	private static volatile Context appContext;
}
