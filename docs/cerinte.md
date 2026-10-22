# Cerințe — sistem distribuit de stocare a fișierelor

Etapa 1: operațiile, modelul metadatelor, cerințele nefuncționale, scenariul demonstrației
și schița protocolului.

## 1. Descriere pe scurt

Sistemul stochează fișiere pe mai multe noduri. Fiecare fișier are **două copii pe noduri
distincte** și rămâne accesibil când un nod cade. Nodurile noi se adaugă fără oprirea
serviciului și sunt descoperite automat.

Componente:

- **Client** — interfață în terminal (`put`, `get`, `ls`, `rm`, `nodes`, `exit`).
- **Noduri de stocare** — păstrează fișierele în `data/<id>/`, de exemplu `data/n2/`.
- **Coordonator** — unul dintre noduri (nu un proces separat). Ține metadatele fișierelor și
  lista nodurilor. Până în Etapa 5 este stabilit cu `--coordinator`; apoi este ales cu Bully.

Metadatele trec prin coordonator, iar conținutul fișierelor circulă direct între client și
noduri (ca în HDFS), ca coordonatorul să nu devină gâtul de sticlă al sistemului.

### Ipoteze de lucru

Obiectivele din secțiunea 4 sunt valabile doar în interiorul acestor ipoteze:

1. Rețea locală (LAN sau `localhost`), latență mică, fără partiționări de durată.
2. Cel mult **un nod căzut** la un moment dat; între două căderi sistemul are timp să
   refacă a doua copie (Etapa 6).
3. Defecte de tip **crash-stop**: un nod fie funcționează corect, fie se oprește. Nu tratăm
   defecte bizantine (noduri care mint sau corup date intenționat).
4. Ceasurile nodurilor sunt aproximativ sincronizate (același calculator sau NTP); le folosim
   doar pentru loguri și timeout-uri, nu pentru ordonarea evenimentelor.
5. Un singur client scrie un anumit fișier la un moment dat (nu tratăm scrieri concurente pe
   același nume).
6. Nu tratăm autentificarea și criptarea traficului.

## 2. Cerințe funcționale — operațiile

Format: comanda, pașii prin sistem, rezultatul, erorile tratate.

| Operație | Comanda | Pașii prin sistem | Rezultat | Erori tratate |
|---|---|---|---|---|
| **Încărcare** | `put raport.pdf` | 1. clientul calculează dimensiunea și SHA-256; 2. cere coordonatorului (`ALLOCATE`) două noduri active, distincte; 3. trimite fișierul primului nod (`PUT`); 4. primul nod verifică checksum-ul și îl copiază pe al doilea (`REPLICATE`); 5. după ambele confirmări, primul nod trimite `COMMIT`, iar coordonatorul înregistrează metadatele | ID-ul fișierului și cele două noduri | mai puțin de 2 noduri active: refuz, nimic stocat · fișier local inexistent sau director: eroare în client, nimic trimis · nume deja existent: refuz (vezi 2.1) · fișier peste dimensiunea maximă: refuz · checksum greșit la nod: `ERROR`, clientul reîncearcă o dată · o copie eșuată: primul nod cere coordonatorului alt nod și reîncearcă; dacă nu există, șterge copia locală și raportează eșec |
| **Descărcare** | `get raport.pdf ./out/` | 1. clientul cere coordonatorului locațiile copiilor (`LOCATE`); 2. descarcă de la primul nod activ (`GET`); 3. verifică checksum-ul; 4. scrie fișierul în director | fișierul salvat local, cu checksum verificat | fișier necunoscut: `NOT_FOUND` · primul nod nu răspunde sau checksum greșit: încearcă a doua copie · nicio copie accesibilă: eroare explicită · directorul destinație inexistent: este creat · fișier local cu același nume: suprascris doar după descărcarea completă (scriere într-un fișier temporar, apoi redenumire) |
| **Listare** | `ls` | 1. clientul trimite `LIST` coordonatorului; 2. coordonatorul răspunde cu toate metadatele confirmate (doar fișierele cu `COMMIT`) | tabel: nume, ID, dimensiune, checksum scurt, nodurile cu copii și starea lor | coordonator indisponibil: eroare explicită · niciun fișier: mesaj „niciun fișier stocat” · un nod cu o copie este căzut: fișierul apare, nodul este marcat `CAZUT` și fișierul ca „1/2 copii” |
| **Ștergere** | `rm raport.pdf` | 1. clientul trimite `DELETE` coordonatorului; 2. coordonatorul marchează fișierul ca șters (dispare imediat din `ls` și `get`); 3. coordonatorul trimite `REMOVE` fiecărui nod care are o copie; 4. nodurile șterg fișierul local și confirmă | confirmare de ștergere | fișier necunoscut: `NOT_FOUND` · **un nod cu o copie este căzut**: ștergerea reușește pentru utilizator; coordonatorul păstrează o listă de ștergeri în așteptare pentru acel nod și trimite `REMOVE` când nodul revine. Dacă nodul revine și raportează un fișier șters (Etapa 6), coordonatorul îi cere să-l șteargă, nu îl readaugă în metadate |
| **Starea sistemului** | `nodes` | 1. clientul trimite `NODES_STATUS` coordonatorului; 2. coordonatorul răspunde cu lista nodurilor cunoscute | tabel: ID, adresă:port, stare (`ACTIV`/`SUSPECT`/`CAZUT`), ultimul semn de viață (acum X s), număr de fișiere, marcaj pentru coordonator | coordonator indisponibil: eroare explicită (după Etapa 5 clientul reîncearcă după realegere) |
| **Adăugarea unui nod nou** (perspectiva sistemului) | `java … StorageNode --id n4 --port 7004 --dir data/n4` | 1. n4 pornește, creează `data/n4/`; 2. anunță prin UDP multicast `HELLO` (ID, adresă, port); 3. coordonatorul îl adaugă în lista nodurilor cu starea `ACTIV` și răspunde cu cine este coordonator; 4. n4 începe să trimită heartbeat (Etapa 3); 5. de la următorul `ALLOCATE`, n4 poate fi ales pentru copii noi | n4 apare în `nodes` în cel mult 3 s; încărcările ulterioare îl pot folosi | ID deja folosit de un nod activ: coordonatorul refuză, n4 se oprește cu mesaj · director cu fișiere vechi: n4 le raportează (Etapa 6), iar coordonatorul le acceptă doar dacă le are în metadate · fișierele existente **nu** sunt mutate automat pe n4 (fără rebalansare) |

### 2.1 Decizii suplimentare

- **Identificarea fișierelor.** Intern, fiecare fișier are un **ID** (UUID) generat de
  coordonator. Utilizatorul lucrează cu **numele**; coordonatorul ține și o tabelă
  nume → ID. Pe disc, nodul salvează fișierul sub ID (`data/n2/<uuid>`), cu un fișier
  `<uuid>.meta` lângă el (nume, dimensiune, checksum), ca metadatele să poată fi
  reconstruite din ce raportează nodurile (Etapa 6). Comenzile `get` și `rm` acceptă fie
  numele, fie ID-ul.
- **Încărcarea unui nume existent.** Este **refuzată** cu mesajul „fișierul există deja;
  folosiți `rm` mai întâi”. Am ales refuzul în locul suprascrierii pentru că suprascrierea
  ar cere actualizarea atomică a ambelor copii, ceea ce depășește proiectul.
- **Dimensiunea maximă.** **1 GiB** per fișier. Limita protejează discul nodurilor și
  păstrează transferul pe un singur flux TCP fără reluare parțială. Fișierele goale
  (0 octeți) sunt permise.
- **Numele fișierelor.** Doar numele de bază, fără `/` sau `\`, maxim 255 de caractere,
  codificat UTF-8.
- **Fișiere incomplete.** Un fișier fără `COMMIT` nu apare în `ls` și nu poate fi descărcat.
  Nodurile scriu întâi în `<uuid>.part` și redenumesc doar după verificarea checksum-ului.
- **Regula de alegere a nodurilor pentru o copie nouă** (folosită din Etapa 4): dintre
  nodurile `ACTIV`, coordonatorul alege cele două cu **cei mai puțini octeți stocați**; la
  egalitate, ordinea ID-urilor. Cele două noduri sunt mereu distincte. Coordonatorul se
  poate alege și pe sine, pentru că este și nod de stocare.

## 3. Modelul de date

Coordonatorul ține în memorie două tabele: metadatele fișierelor și lista nodurilor.
Clasele corespunzătoare sunt în `src/main/java/ro/ubm/sd/common/`.

### 3.1 Metadatele unui fișier — `FileMetadata`

| Câmp | Tip Java | Exemplu | Stabilit de |
|---|---|---|---|
| identificator | `UUID` | `3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90` | coordonator, la `ALLOCATE` |
| nume | `String` | `raport.pdf` | utilizator |
| dimensiune | `long` (octeți) | `482133` | client |
| checksum | `String` (SHA-256, hex) | `9f86d081…` | client, verificat de fiecare nod |
| locațiile copiilor | `List<String>` (ID-uri de nod) | `[n2, n3]` | coordonator, după `COMMIT` |

Tabela: `Map<UUID, FileMetadata>`, plus un index `Map<String, UUID>` pentru căutarea după nume.

### 3.2 Un nod din listă — `NodeInfo`

| Câmp | Tip Java | Exemplu |
|---|---|---|
| ID nod | `String` | `n2` |
| adresă și port | `String`, `int` | `192.168.37.102`, `7002` |
| stare | `enum NodeState { ACTIV, SUSPECT, CAZUT }` | `ACTIV` (folosit din Etapa 3) |
| ultimul semn de viață | `long` (ms) | `System.currentTimeMillis()` la ultimul heartbeat |

Tabela: `Map<String, NodeInfo>` (cheia este ID-ul nodului). Nu există o listă fixă de vecini:
nodurile intră în tabelă prin descoperire.

Tranziții de stare (Etapa 3): `ACTIV` → `SUSPECT` după 2 heartbeat-uri lipsă (~2 s),
`SUSPECT` → `CAZUT` după încă 3 s fără semn de viață; orice heartbeat readuce nodul în `ACTIV`.

### 3.3 Checksum

Calculat cu `MessageDigest.getInstance("SHA-256")` din JDK (clasa `Checksums`). Clientul îl
calculează la `put`, fiecare nod îl verifică la primire, iar clientul îl verifică la `get`.
Dovedește că fișierul descărcat este identic cu cel încărcat, indiferent de nodul care l-a servit.

### 3.4 Recuperarea metadatelor (pregătire pentru Etapa 6)

Dacă coordonatorul cade, metadatele din memoria lui se pierd. Planul:

1. Fiecare nod păstrează lângă fiecare fișier un `<uuid>.meta` cu nume, dimensiune, checksum.
2. Noul coordonator trimite `REPORT_FILES` tuturor nodurilor active.
3. Fiecare nod răspunde cu lista `(uuid, nume, dimensiune, checksum)` a fișierelor deținute.
4. Coordonatorul reconstruiește `Map<UUID, FileMetadata>` grupând după UUID; locațiile copiilor
   sunt nodurile care au raportat acel UUID.
5. Fișierele cu o singură copie raportată sunt replicate pe un nod nou (regula din 2.1).

## 4. Cerințe nefuncționale și obiective măsurabile

| Cerință | Obiectiv măsurabil | Justificare | Cum se măsoară | Etapa |
|---|---|---|---|---|
| Integritate | 0 fișiere cu checksum diferit din 100 de descărcări | SHA-256 detectează orice modificare; o singură eroare înseamnă un bug | script care încarcă și descarcă 100 de fișiere de dimensiuni aleatoare (1 KB – 10 MB) și compară SHA-256 | 2 |
| Descoperire | un nod nou apare în lista celorlalți în cel mult **3 s** | anunțul multicast se repetă la 1 s; 3 s permit pierderea a două pachete UDP | diferența dintre timestamp-ul „pornit” din logul nodului nou și „nod nou descoperit” din logul coordonatorului | 2 |
| Detectarea defectelor | un nod oprit cu `kill -9` este marcat `CAZUT` în cel mult **5 s** | heartbeat la 1 s, `SUSPECT` după 2 s, `CAZUT` după încă 3 s | loguri cu timestamp (ms) pe fiecare nod: momentul `kill` vs. momentul „marcat CAZUT” | 3 |
| Scalabilitate | debitul de încărcare cu 3 noduri este cu **cel puțin 30%** mai mare decât cu 1 nod | conținutul nu trece prin coordonator, deci mai multe noduri înseamnă mai multe discuri și conexiuni | 10 fișiere de 100 MB, încărcate din 2 clienți în paralel; timp total cu 1 nod vs. 3 noduri (cu 1 nod, rulăm temporar cu o singură copie) | 4 |
| Echilibrarea încărcării | după 30 de încărcări pe 3 noduri, diferența de octeți stocați între cel mai încărcat și cel mai puțin încărcat nod este sub **25%** | regula „cei mai puțini octeți” | `du -sb data/n*` după script | 4 |
| Coordonare | noul coordonator este ales în cel mult **10 s** după căderea celui vechi | 5 s detecție + timeout-uri Bully (2 s pentru `ELECTION`, 3 s pentru `COORDINATOR`) | loguri Bully: `kill` pe coordonator vs. mesajul `COORDINATOR` primit de toate nodurile | 5 |
| Replicare | fiecare fișier are **exact 2 copii** pe noduri distincte | cerința temei | comanda `ls` și numărarea fișierelor `<uuid>` din `data/n*/` | 6 |
| Recuperare | după realegere, `ls` arată 100% din fișierele confirmate înainte de cădere în cel mult **5 s** de la alegere | reconstruirea din rapoartele nodurilor | script: `ls` înainte și după, comparat | 6 |
| Disponibilitate | după oprirea **oricărui** nod (inclusiv coordonatorul), 100% din fișiere rămân descărcabile | două copii pe noduri distincte, cel mult un nod căzut | script care descarcă toate fișierele după `kill -9` și verifică checksum-urile | 7 |

## 5. Scenariul demonstrației

Testul de acceptanță al proiectului: rulat parțial la fiecare etapă, prezentat complet în
săptămâna 14. Fiecare nod și clientul rulează în terminal propriu, din rădăcina proiectului,
după `mvn -q package`. Prescurtare: `CP=target/classes`.

| Pas | Acțiunea operatorului | Comanda exactă | Ce trebuie să se vadă | Din etapa |
|---|---|---|---|---|
| 0 | pregătire | `rm -rf data/ && mvn -q package && head -c 500000 /dev/urandom > raport.pdf && sha256sum raport.pdf` | directoare goale, fișier de test cu checksum afișat | 1 |
| 1 | pornește n1, n2, n3 în terminale separate | T1: `java -cp $CP ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator`<br>T2: `java -cp $CP ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2`<br>T3: `java -cp $CP ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3` | fiecare nod îi descoperă pe ceilalți; n1 este coordonator | 2 |
| 1b | pornește clientul | T4: `java -cp $CP ro.ubm.sd.client.Client --coordinator localhost:7001` apoi `nodes` | 3 noduri `ACTIV`, n1 marcat coordonator | 2 |
| 2 | încarcă un fișier | `put raport.pdf` | ID-ul fișierului și două noduri distincte care îl dețin | 2 |
| 3 | descarcă fișierul | `get raport.pdf ./out/` apoi, în alt terminal, `sha256sum out/raport.pdf` | fișierul descărcat, cu checksum identic cu cel de la pasul 0 | 2 |
| 4 | pornește n4 fără a opri ceva | T5: `java -cp $CP ro.ubm.sd.node.StorageNode --id n4 --port 7004 --dir data/n4` apoi în client `nodes`, `put alt.pdf` | n4 apare în `nodes`; următoarele încărcări îl pot folosi (fiind gol, n4 este ales) | 4 |
| 5 | oprește brusc un nod care deține raport.pdf | în client `ls` (aflăm nodurile, ex. n2), apoi `kill -9 $(pgrep -f "id n2")`; după ~5 s `nodes`, apoi `get raport.pdf ./out2/` | n2 marcat `CAZUT`; `get` reușește din cealaltă copie | 3 |
| 6 | oprește brusc coordonatorul | `kill -9 $(pgrep -f "id n1")` | nodurile rămase aleg un nou coordonator (cel cu ID-ul cel mai mare, n4) în ≤ 10 s; logurile arată `ELECTION` / `COORDINATOR` | 5 |
| 7 | listare și descărcare după realegere | în client `ls`, apoi `get raport.pdf ./out3/` și `sha256sum out3/raport.pdf` | metadatele sunt reconstruite; fișierul rămâne accesibil, checksum identic | 6 |

Observații:
- Pe Windows, `kill -9` se înlocuiește cu închiderea ferestrei nodului sau
  `taskkill /F /PID <pid>` (PID-ul se află cu `jps -l -m`).
- Pentru Bully comparăm ID-urile ca șiruri (`n4` > `n3` > `n2`); vom folosi ID-uri de aceeași
  lungime ca să evităm ordinea `n10` < `n2`.

## 6. Schița protocolului

TCP transportă un flux de octeți, deci fiecare mesaj are un **antet pe o singură linie JSON**
terminat cu `\n`, iar când există conținut de fișier, acesta urmează imediat după antet ca
**exact `size` octeți**. Formatul exact se fixează în Etapa 2.

Exemplu de antet: `{"type":"PUT","id":"3f2a…","name":"raport.pdf","size":482133,"checksum":"9f86…","next":"n3"}`

### 6.1 Operații pe fișiere

| Mesaj | De la → către | Conținut | Răspuns |
|---|---|---|---|
| `ALLOCATE` | client → coordonator | nume, dimensiune | `NODES` cu ID-ul fișierului și 2 noduri (ID, adresă, port), sau `ERROR` (motiv: `EXISTS`, `TOO_LARGE`, `NOT_ENOUGH_NODES`) |
| `PUT` | client → nod | ID, nume, dimensiune, checksum, nodul pentru copie + octeții fișierului | `STORED` sau `ERROR` |
| `REPLICATE` | nod → nod | ca `PUT`, fără nodul următor + octeții | `STORED` sau `ERROR` |
| `COMMIT` | nod → coordonator | ID fișier, nodurile care îl dețin | `OK` |
| `LOCATE` | client → coordonator | nume sau ID | `LOCATIONS` cu metadatele, sau `NOT_FOUND` |
| `GET` | client → nod | ID fișier | antet cu dimensiunea + octeții, sau `NOT_FOUND` |
| `LIST` | client → coordonator | — | `FILES` cu lista metadatelor (nume, ID, dimensiune, checksum, noduri și starea lor) |
| `DELETE` | client → coordonator | nume sau ID | `DELETED` sau `NOT_FOUND` |
| `REMOVE` | coordonator → nod | ID fișier | `REMOVED` sau `NOT_FOUND` (ambele considerate succes) |
| `NODES_STATUS` | client → coordonator | — | `NODES_LIST` cu ID, adresă, port, stare, ultimul semn de viață, octeți stocați, coordonator |

### 6.2 Mesaje din etapele următoare (doar enumerate)

| Mesaj | Transport | De la → către | Conținut | Etapa |
|---|---|---|---|---|
| `HELLO` | UDP multicast | nod → toți | ID, adresă, port | 2 |
| `WELCOME` | TCP | coordonator → nod nou | ID-ul și adresa coordonatorului | 2 |
| `HEARTBEAT` | UDP/TCP | nod → coordonator | ID, octeți stocați | 3 |
| `ELECTION` / `ANSWER` / `COORDINATOR` | TCP | nod → noduri cu ID mai mare / răspuns / anunț | ID-ul expeditorului | 5 |
| `REPORT_FILES` / `FILES_REPORT` | TCP / RMI-gRPC | coordonator → nod / răspuns | lista `(uuid, nume, dimensiune, checksum)` | 6 |

### 6.3 Pornirea proceselor

Fiecare componentă are propriul `main` și propriile argumente; nimic nu este scris în cod.
Comenzile complete sunt în [`README.md`](../README.md). La finalul Etapei 1 fiecare proces
pornește, creează directorul dacă lipsește și afișează configurația:

```
[n2] pornit: port 7002, director data/n2, rol: nod de stocare
[client] coordonator configurat: localhost:7001; comenzi: put, get, ls, rm, nodes, exit
```

`--coordinator` este o soluție temporară: în Etapa 2 clientul și nodurile îl găsesc prin
descoperire, iar în Etapa 5 coordonatorul este ales, nu numit.