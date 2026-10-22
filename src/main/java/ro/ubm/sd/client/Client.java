package ro.ubm.sd.client;

import ro.ubm.sd.common.Args;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Clientul cu interfata in terminal: put, get, ls, rm, nodes, exit.
 * In Etapa 1 doar citeste configuratia si comenzile; transferul vine in Etapa 2.
 *
 * Exemplu:
 *   java -cp target/classes ro.ubm.sd.client.Client --coordinator localhost:7001
 */
public class Client {

    private final String coordHost;
    private final int coordPort;

    public Client(String coordHost, int coordPort) {
        this.coordHost = coordHost;
        this.coordPort = coordPort;
    }

    public void run() throws IOException {
        System.out.printf("[client] coordonator configurat: %s:%d; comenzi: put, get, ls, rm, nodes, exit%n",
                coordHost, coordPort);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        while (true) {
            System.out.print("> ");
            System.out.flush();
            String line = in.readLine();
            if (line == null) {
                break; // EOF (Ctrl+D / Ctrl+Z)
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            String cmd = parts[0];
            switch (cmd) {
                case "exit" -> {
                    System.out.println("[client] la revedere");
                    return;
                }
                case "put" -> needArgs(parts, 1, "put <fisier_local>");
                case "get" -> needArgs(parts, 1, "get <nume> [director_destinatie]");
                case "rm" -> needArgs(parts, 1, "rm <nume>");
                case "ls", "nodes" -> notYet(cmd);
                default -> System.out.println("Comanda necunoscuta: " + cmd
                        + " (comenzi: put, get, ls, rm, nodes, exit)");
            }
        }
    }

    private void needArgs(String[] parts, int n, String usage) {
        if (parts.length - 1 < n) {
            System.out.println("Utilizare: " + usage);
        } else {
            notYet(parts[0]);
        }
    }

    private void notYet(String cmd) {
        System.out.println("[client] '" + cmd + "' va fi implementata in Etapa 2");
    }

    private static void usage() {
        System.err.println("Utilizare: Client --coordinator <host>:<port>");
    }

    public static void main(String[] argv) {
        try {
            Args args = Args.parse(argv, Set.of());
            String coord = args.require("coordinator");
            int sep = coord.lastIndexOf(':');
            if (sep <= 0 || sep == coord.length() - 1) {
                throw new IllegalArgumentException("--coordinator trebuie sa fie host:port, nu: " + coord);
            }
            String host = coord.substring(0, sep);
            int port = Integer.parseInt(coord.substring(sep + 1));

            new Client(host, port).run();
        } catch (NumberFormatException e) {
            System.err.println("Eroare: portul coordonatorului nu este numar");
            usage();
            System.exit(2);
        } catch (IllegalArgumentException e) {
            System.err.println("Eroare: " + e.getMessage());
            usage();
            System.exit(2);
        } catch (IOException e) {
            System.err.println("Eroare la citirea comenzilor: " + e.getMessage());
            System.exit(1);
        }
    }
}