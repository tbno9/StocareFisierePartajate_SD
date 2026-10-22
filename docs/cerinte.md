Cerințe — sistem distribuit de stocare a fișierelor

Etapa 1: operațiile, modelul metadatelor, cerințele nefuncționale, scenariul demonstrației și schița protocolului.

1. Descriere pe scurt

Sistemul stochează fișiere pe mai multe noduri. Fiecare fișier are două copii pe noduri distincte și rămâne accesibil când un nod cade. Nodurile noi se adaugă fără oprirea serviciului și sunt descoperite automat.

Componente:

Client — interfață în terminal (put, get, ls, rm, nodes, exit).
Noduri de stocare — păstrează fișierele în data/<id>/, de exemplu data/n2/.
Coordonator — unul dintre noduri (nu un proces separat). Ține metadatele fișierelor și lista nodurilor. Până în Etapa 5 este stabilit cu --coordinator; apoi este ales cu Bully.

Metadatele trec prin coordonator, iar conținutul fișierelor circulă direct între client și noduri (ca în HDFS), ca coordonatorul să nu devină gâtul de sticlă al sistemului.

Ipoteze de lucru

Obiectivele din secțiunea 4 sunt valabile doar în interiorul acestor ipoteze:

Rețea locală (LAN sau localhost), latență mică, fără partiționări de durată.
Cel mult un nod căzut la un moment dat; între două căderi sistemul are timp să refacă a doua copie (Etapa 6).
Defecte de tip crash-stop: un nod fie funcționează corect, fie se oprește. Nu tratăm defecte bizantine (noduri care mint sau corup date intenționat).
Ceasurile nodurilor sunt aproximativ sincronizate (același calculator sau NTP); le folosim doar pentru loguri și timeout-uri, nu pentru ordonarea evenimentelor.
Un singur client scrie un anumit fișier la un moment dat (nu tratăm scrieri concurente pe același nume).
Nu tratăm autentificarea și criptarea traficului.
2. Cerințe funcționale — operațiile

Format: comanda, pașii prin sistem, rezultatul, erorile tratate.

Operație	Comanda	Pașii prin sistem	Rezultat	Erori tratate
Încărcare	put raport.pdf	1. clientul calculează dimensiunea și SHA-256; 2. cere coordonatorului (ALLOCATE) două noduri active, distincte; 3. trimite fișierul primului nod (PUT); 4. primul nod verifică checksum-ul și îl copiază pe al doilea (REPLICATE); 5. după ambele confirmări, primul nod trimite COMMIT, iar coordonatorul înregistrează metadatele	ID-ul fișierului și cele două noduri	mai puțin de 2 noduri active: refuz, nimic stocat · fișier local inexistent sau director: eroare în client, nimic trimis · nume deja existent: refuz (vezi 2.1) · fișier peste dimensiunea maximă: refuz · checksum greșit la nod: ERROR, clientul reîncearcă o dată · o copie eșuată: primul nod cere coordonatorului alt nod și reîncearcă; dacă nu există, șterge copia locală și raportează eșec
Descărcare	get raport.pdf ./out/	1. clientul cere coordonatorului locațiile copiilor (LOCATE); 2. descarcă de la primul nod activ (GET); 3. verifică checksum-ul; 4. scrie fișierul în director	fișierul salvat local, cu checksum verificat	fișier necunoscut: NOT_FOUND · primul nod nu răspunde sau checksum greșit: încearcă a doua copie · nicio copie accesibilă: eroare explicită · directorul destinație inexistent: este creat · fișier local cu același nume: suprascris doar după descărcarea completă (scriere într-un fișier temporar, apoi redenumire)
Listare	ls	1. clientul trimite LIST coordonatorului; 2. coordonatorul răspunde cu toate metadatele confirmate (doar fișierele cu COMMIT)	tabel: nume, ID, dimensiune, checksum scurt, nodurile cu copii și starea lor	coordonator indisponibil: eroare explicită · niciun fișier: mesaj „niciun fișier stocat” · un nod cu o copie este căzut: fișierul apare, nodul este marcat CAZUT și fișierul ca „1/2 copii”
Ștergere	rm raport.pdf	1. clientul trimite DELETE coordonatorului; 2. coordonatorul marchează fișierul ca șters (dispare imediat din ls și get); 3. coordonatorul trimite REMOVE fiecărui nod care are o copie; 4. nodurile șterg fișierul local și confirmă	confirmare de ștergere	fișier necunoscut: NOT_FOUND · un nod cu o copie este căzut: ștergerea reușește pentru utilizator; coordonatorul păstrează o listă de ștergeri în așteptare pentru acel nod și trimite REMOVE când nodul revine. Dacă nodul revine și raportează un fișier șters (Etapa 6), coordonatorul îi cere să-l șteargă, nu îl readaugă în metadate
Starea sistemului	nodes	1. clientul trimite NODES_STATUS coordonatorului; 2. coordonatorul răspunde cu lista nodurilor cunoscute	tabel: ID, adresă:port, stare (ACTIV/SUSPECT/CAZUT), ultimul semn de viață (acum X s), număr de fișiere, marcaj pentru coordonator	coordonator indisponibil: eroare explicită (după Etapa 5 clientul reîncearcă după realegere)
Adăugarea unui nod nou (perspectiva sistemului)	java … StorageNode --id n4 --port 7004 --dir data/n4	1. n4 pornește, creează data/n4/; 2. anunță prin UDP multicast HELLO (ID, adresă, port); 3. coordonatorul îl adaugă în lista nodurilor cu starea ACTIV și răspunde cu cine este coordonator; 4. n4 începe să trimită heartbeat (Etapa 3); 5. de la următorul ALLOCATE, n4 poate fi ales pentru copii noi	n4 apare în nodes în cel mult 3 s; încărcările ulterioare îl pot folosi	ID deja folosit de un nod activ: coordonatorul refuză, n4 se oprește cu mesaj · director cu fișiere vechi: n4 le raportează (Etapa 6), iar coordonatorul le acceptă doar dacă le are în metadate · fișierele existente nu sunt mutate automat pe n4 (fără rebalansare)
2.1 Decizii suplimentare
Identificarea fișierelor. Intern, fiecare fișier are un ID (UUID) generat de coordonator. Utilizatorul lucrează cu numele; coordonatorul ține și o tabelă nume → ID. Pe disc, nodul salvează fișierul sub ID (data/n2/<uuid>), cu un fișier <uuid>.meta lângă el (nume, dimensiune, checksum), ca metadatele să poată fi reconstruite din ce raportează nodurile (Etapa 6). Comenzile get și rm acceptă fie numele, fie ID-ul.
Încărcarea unui nume existent. Este refuzată cu mesajul „fișierul există deja; folosiți rm mai întâi”. Am ales refuzul în locul suprascrierii pentru că suprascrierea ar cere actualizarea atomică a ambelor copii, ceea ce depășește proiectul.
Dimensiunea maximă. 1 GiB per fișier. Limita protejează discul nodurilor și păstrează transferul pe un singur flux TCP fără reluare parțială. Fișierele goale (0 octeți) sunt permise.
Numele fișierelor. Doar numele de bază, fără / sau \, maxim 255 de caractere, codificat UTF-8.
Fișiere incomplete. Un fișier fără COMMIT nu apare în ls și nu poate fi descărcat. Nodurile scriu întâi în <uuid>.part și redenumesc doar după verificarea checksum-ului.
Regula de alegere a nodurilor pentru o copie nouă (folosită din Etapa 4): dintre nodurile ACTIV, coordonatorul alege cele două cu cei mai puțini octeți stocați; la egalitate, ordinea ID-urilor. Cele două noduri sunt mereu distincte. Coordonatorul se poate alege și pe sine, pentru că este și nod de stocare.
