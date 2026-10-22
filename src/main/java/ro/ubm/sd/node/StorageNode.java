package ro.ubm.sd.node;

import ro.ubm.sd.common.Args;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Nodul de stocare. Toate nodurile ruleaza acelasi cod; oricare poate fi coordonator.
 * Pana la Etapa 5 (Bully) coordonatorul se stabileste cu --coordinator.
 *
 * Exemplu:
 *   java -cp target/classes ro.ubm.sd.node.StorageNode --id n1 --port 7001 --dir data/n1 --coordinator
 */
public class StorageNode {

    private final String id;
    private final String host;
    private final int port;
    private final Path dataDir;
    private final boolean coordinator;

    public StorageNode(String id, String host, int port, Path dataDir, boolean coordinator) {
        this.id = id;
        this.host = host;
        this.port = port;
        this.dataDir = dataDir;
        this.coordinator = coordinator;
    }

    public void start() throws IOException {
        Files.createDirectories(dataDir);
        String rol = coordinator ? "coordonator" : "nod de stocare";
        System.out.printf("[%s] pornit: port %d, director %s, rol: %s%n",
                id, port, dataDir, rol);
        // Etapa 2: aici pornesc serverul TCP si descoperirea prin UDP multicast.
        // Etapa 3: aici porneste trimiterea de heartbeat-uri.
    }

    public String getId() { return id; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public Path getDataDir() { return dataDir; }
    public boolean isCoordinator() { return coordinator; }

    private static void usage() {
        System.err.println("Utilizare: StorageNode --id <id> --port <port> --dir <director> "
                + "[--host <adresa>] [--coordinator]");
    }

    public static void main(String[] argv) {
        try {
            Args args = Args.parse(argv, Set.of("coordinator"));
            String id = args.require("id");
            int port = args.requireInt("port");
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port invalid: " + port);
            }
            Path dir = Paths.get(args.require("dir"));
            String host = args.get("host", "localhost");

            new StorageNode(id, host, port, dir, args.has("coordinator")).start();
        } catch (IllegalArgumentException e) {
            System.err.println("Eroare: " + e.getMessage());
            usage();
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Nu pot crea directorul de date: " + e.getMessage());
            System.exit(1);
        }
    }
}