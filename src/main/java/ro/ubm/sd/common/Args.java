package ro.ubm.sd.common;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Parser simplu pentru argumente de forma "--cheie valoare" si "--flag".
 * Folosit atat de nod, cat si de client, ca nimic sa nu fie scris in cod.
 */
public final class Args {

    private final Map<String, String> values = new HashMap<>();
    private final Set<String> flags = new HashSet<>();

    private Args() {
    }

    /**
     * @param args      argumentele din main
     * @param flagNames numele argumentelor fara valoare (ex. "coordinator" pentru nod)
     */
    public static Args parse(String[] args, Set<String> flagNames) {
        Args result = new Args();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) {
                throw new IllegalArgumentException("Argument neasteptat: " + a);
            }
            String key = a.substring(2);
            if (flagNames.contains(key)) {
                result.flags.add(key);
            } else {
                if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                    throw new IllegalArgumentException("Lipseste valoarea pentru --" + key);
                }
                result.values.put(key, args[++i]);
            }
        }
        return result;
    }

    public String require(String key) {
        String v = values.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Argument obligatoriu lipsa: --" + key);
        }
        return v;
    }

    public String get(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public int requireInt(String key) {
        String v = require(key);
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + key + " trebuie sa fie numar, nu: " + v);
        }
    }

    public boolean has(String flag) {
        return flags.contains(flag);
    }
}