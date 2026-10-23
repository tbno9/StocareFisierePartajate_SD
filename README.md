# sd-storage — sistem distribuit de stocare a fișierelor

Proiect la Sisteme Distribuite. Fișierele sunt stocate pe mai multe noduri, fiecare fișier
are două copii pe noduri distincte și rămâne accesibil când un nod cade. Unul dintre noduri
este coordonator și ține metadatele; conținutul fișierelor circulă direct între client și noduri.

Cerințele, modelul de date, obiectivele măsurabile, scenariul demonstrației și schița
protocolului sunt în [`docs/cerinte.md`](docs/cerinte.md).

## Autor

Proiect individual: Tinc Bogdana Nelia Oana

## Cerințe

- JDK 17 sau mai nou
- Maven 3.8+
- Git

Verificare: `java -version && mvn -version && git --version`

## Compilare

```bash
mvn -q package
```

Clasele compilate ajung în `target/classes`.

Din IntelliJ (dacă `mvn` nu este instalat separat): panoul **Maven** din dreapta →
`sd-storage` → **Lifecycle** → dublu-click pe **package**.

## Pornire

Fiecare componentă este un proces separat. Porturile, directoarele și coordonatorul se dau
din linia de comandă.

### Nodurile (câte un terminal pentru fiecare)

```bash
java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
java -cp target/classes ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2
java -cp target/classes ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3
```

| Argument | Obligatoriu | Descriere |
|---|---|---|
| `--id` | da | identificatorul nodului (ex. `n2`) |
| `--port` | da | portul TCP pe care ascultă nodul |
| `--dir` | da | directorul local de date; se creează dacă lipsește |
| `--host` | nu | adresa anunțată celorlalți (implicit `localhost`) |
| `--coordinator` | nu | nodul pornește ca coordonator (temporar, până la Etapa 5) |

Ieșire așteptată:

```
[n2] pornit: port 7002, director data/n2, rol: nod de stocare
```

### Clientul (al patrulea terminal)

```bash
java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001
```

Ieșire așteptată:

```
[client] coordonator configurat: localhost:7001; comenzi: put, get, ls, rm, nodes, exit
```

### Script pentru toate cele 3 noduri

- Linux / Git Bash: `./scripts/start-local.sh` (nodurile rulează în fundal, logurile în `logs/`;
  oprire cu `kill $(cat logs/*.pid)`)
- Windows PowerShell: `powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1`
  (deschide câte o fereastră pentru fiecare nod)

## Structura

```
sd-storage/
├── README.md
├── pom.xml
├── docs/cerinte.md
├── scripts/start-local.sh, start-local.ps1
├── src/main/java/ro/ubm/sd/
│   ├── client/Client.java        # interfața în terminal
│   ├── node/StorageNode.java     # nodul de stocare (poate fi și coordonator)
│   └── common/Args.java          # citirea argumentelor din linia de comanda
└── data/                         # directoarele nodurilor, ignorate de Git
```
