# sd-storage — stocare distribuită a fișierelor

Necesită JDK 17+ și Maven.

## Compilare

```bash
mvn -q package
```

## Pornire

Câte un terminal pentru fiecare nod:

```bash
java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
java -cp target/classes ro.ubm.sd.node.StorageNode --id n2 --port 7002 --dir data/n2
java -cp target/classes ro.ubm.sd.node.StorageNode --id n3 --port 7003 --dir data/n3
```

Sau toate trei odată: `powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1`

Clientul, în al patrulea terminal:

```bash
java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001
```
