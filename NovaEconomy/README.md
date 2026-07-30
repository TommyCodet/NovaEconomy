# NovaEconomy

Eigenständiges Economy- und Auftragssystem für **Paper 26.2**. Das Plugin verwendet **keine VaultAPI**, **kein EssentialsX Economy** und keine andere Economy-Implementierung. Konten, Reservierungen, Auszahlungen, Transaktionshistorie, Aufträge und Lieferungen werden vollständig von NovaEconomy in SQLite verwaltet.

- Plugin: `NovaEconomy`
- Autor: `Txmmy`
- Zielplattform: `Paper 26.2`
- Java: `25`
- Build-System: `Maven`
- Datenbank: `SQLite`

## Projektstruktur

```text
NovaEconomy/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/de/txmmy/novaeconomy/
    │   └── resources/
    │       ├── plugin.yml
    │       ├── config.yml
    │       ├── messages.yml
    │       ├── migrations.yml
    │       └── db/migration/V1__initial_schema.sql
    └── test/java/de/txmmy/novaeconomy/
```

Es existieren absichtlich keine Gradle-Dateien.

## Build

Voraussetzungen:

1. JDK 25 muss aktiv sein (`java -version` und `javac -version`).
2. Maven 3.9 oder neuer muss installiert sein.
3. Internetzugriff auf das PaperMC-Maven-Repository und Maven Central ist beim ersten Build erforderlich.

Build und Tests:

```bash
mvn clean package
```

Nur Tests:

```bash
mvn test
```

Das fertige, inklusive SQLite-Treiber geschattete Plugin liegt anschließend unter:

```text
target/NovaEconomy-1.0.0.jar
```

## Installation

1. Paper 26.2 mit Java 25 starten und anschließend wieder stoppen.
2. `target/NovaEconomy-1.0.0.jar` nach `plugins/` kopieren.
3. Server starten.
4. Beim ersten Start entstehen:

```text
plugins/NovaEconomy/config.yml
plugins/NovaEconomy/messages.yml
plugins/NovaEconomy/novaeconomy.db
```

5. Werte in `config.yml` und Texte in `messages.yml` bei Bedarf anpassen und den Server neu starten.

## Eigenständiges Economy-System

NovaEconomy besitzt eigene Konten und benötigt keine Service-Bridge. Ein Konto wird beim ersten Join oder bei der ersten Kontoverwendung angelegt. Das Startguthaben wird über `economy.starting-balance` festgelegt.

Geld wird intern als ganzzahliger Betrag in der kleinsten Währungseinheit gespeichert. Bei zwei Nachkommastellen entspricht beispielsweise `2,50` intern `250`. Dadurch werden Gleitkommafehler vermieden.

Jede Änderung wird zusätzlich in `account_transactions` protokolliert. Zu den Typen gehören unter anderem:

- `ORDER_RESERVE`
- `ORDER_DELIVERY_PAYOUT`
- `ORDER_REFUND`
- `PLAYER_PAYMENT_SENT`
- `PLAYER_PAYMENT_RECEIVED`
- `ADMIN_SET`, `ADMIN_ADD`, `ADMIN_TAKE`

## Auftragssystem

### Auftrag erstellen

```text
/order <block> <anzahl> <preis-pro-stück>
```

Beispiel:

```text
/order stone 1000 2.50
```

Beim Erstellen wird der Gesamtpreis in einer SQLite-Transaktion vom Konto abgezogen und im Auftrag als Restreserve gespeichert. Der Auftrag wird nicht angelegt, wenn das Konto nicht ausreichend gedeckt ist.

Es werden nur Materialien akzeptiert, die sowohl Block als auch Inventar-Item sind. Technische Blöcke wie Luft, Portale oder Wasser können daher nicht bestellt werden.

### Aufträge durchsuchen

```text
/order search
/order search <block>
```

Deutsche Aliase werden ebenfalls erkannt:

```text
/order suche
/order suche stone
```

Das GUI zeigt nur aktive Fremdaufträge, für die der Spieler mindestens ein passendes Item im Hauptinventar besitzt.

Liefersteuerung im GUI:

- Rechtsklick: 1 Item
- Linksklick: bis zum konfigurierten normalen Klicklimit, standardmäßig 64
- Shift-Klick: so viele Items wie möglich

Die Items werden zuerst aus dem Inventar entfernt. Kann die Datenbankbuchung nicht erfolgen, werden sie vollständig zurückgegeben beziehungsweise bei vollem Inventar am Spieler gedroppt. Bei erfolgreicher Buchung wird der Lieferant direkt aus der reservierten Auftragssumme bezahlt.

### Gelieferte Items abholen

```text
/order collect
```

Alle seit der letzten Abholung gelieferten Items werden zusammengefasst ausgegeben. Passt etwas nicht ins Inventar, wird es am Standort des Spielers gedroppt.

### Auftrag abbrechen

```text
/order cancel <auftrags-id>
```

Nur der Auftraggeber kann einen aktiven Auftrag abbrechen. Das noch nicht für Lieferungen verwendete Restguthaben wird in derselben Datenbanktransaktion zurückerstattet. Bereits gelieferte Items bleiben über `/order collect` abholbar.

## Economy-Befehle

NovaEconomy enthält ergänzende Befehle, damit das eigenständige Kontensystem ohne Fremdplugin administriert und verwendet werden kann.

```text
/money
/money balance
/money balance <spieler>
/money pay <spieler> <betrag>
/money admin set <spieler> <betrag>
/money admin add <spieler> <betrag>
/money admin take <spieler> <betrag>
```

Aliase: `/balance`, `/bal`, `/geld`.

Offline-Ziele werden nur verwendet, wenn sie bereits im Server-Cache bekannt sind. Dadurch führt der Befehl keine blockierende Profil-Webanfrage aus.

## Befehlsübersicht

| Befehl | Beschreibung |
|---|---|
| `/order <block> <anzahl> <preis>` | Auftrag erstellen und Gesamtpreis reservieren |
| `/order search [block]` | lieferbare aktive Aufträge im GUI öffnen |
| `/order collect` | gelieferte Items abholen |
| `/order cancel <id>` | eigenen aktiven Auftrag abbrechen |
| `/money [balance]` | eigenen Kontostand anzeigen |
| `/money balance <spieler>` | fremden Kontostand anzeigen |
| `/money pay <spieler> <betrag>` | Geld überweisen |
| `/money admin set/add/take ...` | Kontostand administrativ ändern |

## Permissions

| Permission | Standard | Zweck |
|---|---:|---|
| `novaeconomy.order.use` | alle | Grundzugriff auf `/order` |
| `novaeconomy.order.create` | alle | Aufträge erstellen |
| `novaeconomy.order.search` | alle | GUI öffnen und liefern |
| `novaeconomy.order.collect` | alle | Lieferungen abholen |
| `novaeconomy.order.cancel` | alle | eigene Aufträge abbrechen |
| `novaeconomy.money.use` | alle | Grundzugriff auf `/money` |
| `novaeconomy.money.balance` | alle | eigenen Kontostand sehen |
| `novaeconomy.money.balance.others` | OP | fremde Kontostände sehen |
| `novaeconomy.money.pay` | alle | Geld überweisen |
| `novaeconomy.money.admin` | OP | Konten administrieren |

## Konfiguration

Wichtige Optionen in `config.yml`:

```yaml
economy:
  starting-balance: 1000.00
  currency-symbol: "€"
  locale: "de-DE"

orders:
  max-amount-per-order: 10000000
  max-active-orders-per-player: 25
  normal-click-delivery-limit: 64
  gui-page-size: 45

storage:
  sqlite-file: "novaeconomy.db"
  busy-timeout-ms: 5000
```

`messages.yml` verwendet MiniMessage-Formatierung. Dynamische Werte werden als unformatierter Text eingesetzt und können daher keine MiniMessage-Tags einschleusen.

## SQLite-Tabellen

Die initiale Migration befindet sich unter:

```text
src/main/resources/db/migration/V1__initial_schema.sql
```

Sie wird über `migrations.yml` registriert. Bereits ausgeführte Migrationen werden in `schema_migrations` gespeichert.

### `accounts`

Speichert UUID, letzten Namen, Kontostand und Zeitstempel.

### `orders`

Speichert Auftraggeber, Material, Sollmenge, gelieferte und abgeholte Menge, Stückpreis, Restreserve und Status (`ACTIVE`, `COMPLETED`, `CANCELLED`).

### `deliveries`

Unveränderliche Historie jeder erfolgreichen Teillieferung mit Lieferant, Menge und Auszahlung.

### `account_transactions`

Ledger aller Kontobewegungen mit Betrag, Kontostand nach Buchung und optionaler Referenz.

### `schema_migrations`

Versionshistorie der ausgeführten SQL-Migrationen.

## Transaktions- und Sicherheitsverhalten

Folgende Vorgänge sind jeweils atomare SQLite-Transaktionen:

- Kontoanlage
- Auftragsanlage plus Geldreservierung
- Lieferung plus Auftragsfortschritt plus Lieferantenauszahlung
- Stornierung plus Rückerstattung
- Spielerüberweisung
- administrative Kontenänderung
- Markierung einer Abholung

SQLite läuft mit aktivierten Foreign Keys, WAL-Journal, `synchronous=NORMAL` und konfigurierbarem Busy Timeout.

Die gesamte Economy-Logik ist serverseitig. Der GUI-Inhalt ist keine Buchungsquelle; beim Klick wird der Auftragsstatus erneut aus SQLite gelesen und validiert.

## Tests

Die enthaltenen JUnit-Tests prüfen:

- Geldparser mit Punkt und Komma
- Ablehnung von mehr als zwei Nachkommastellen
- exakte Multiplikation und Overflow-Erkennung
- Zerlegung von SQL-Migrationsskripten einschließlich Semikolons in Strings

Ausführen:

```bash
mvn test
```

Für einen vollständigen Integrationstest:

1. `mvn clean package` ausführen.
2. Eine frische Paper-26.2-Testinstanz mit Java 25 starten.
3. Zwei Testspieler verbinden.
4. Guthaben mit `/money` kontrollieren.
5. Auftrag erstellen.
6. Mit dem zweiten Spieler passende Items aufnehmen und `/order search` öffnen.
7. Teilmenge liefern und Auszahlungen prüfen.
8. Mit dem Auftraggeber `/order collect` ausführen.
9. Einen teilweise erfüllten Auftrag abbrechen und Rückerstattung sowie Restabholung prüfen.
10. Server neu starten und Persistenz erneut prüfen.

## Hinweise für Erweiterungen

Neue Datenbankänderungen sollten als neue Datei wie `V2__beschreibung.sql` angelegt und in `migrations.yml` mit einer höheren Versionsnummer registriert werden. Bereits ausgelieferte Migrationen sollten nicht nachträglich verändert werden.
