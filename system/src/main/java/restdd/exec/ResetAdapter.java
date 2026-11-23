package restdd.exec;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Returns a deployment to its snapshot state before every suite run and every isolated re-run (D28).
 * Implementations: {@link NoReset} (RESTCountries, read-only) and {@link CommandReset} (Dolibarr database dump,
 * Podman cleanup keeping the fixture image, nocodb container/SQLite restore), each driven by a project script.
 */
public interface ResetAdapter {

    /** Thrown when the deployment cannot be reset or is not healthy afterwards (ERROR-INFRA). */
    final class ResetException extends Exception {
        public ResetException(String message) {
            super(message);
        }
    }

    String name();

    /** Takes the snapshot once, after deployment and fixture loading, before any test. */
    void snapshot(ObjectNode record) throws ResetException;

    /** Restores the snapshot and waits until the deployment is healthy. */
    void restore(ObjectNode record) throws ResetException;

    /** For read-only targets. */
    final class NoReset implements ResetAdapter {
        @Override
        public String name() {
            return "none";
        }

        @Override
        public void snapshot(ObjectNode record) {
            record.put("snapshot", "none");
        }

        @Override
        public void restore(ObjectNode record) {
            record.put("restore", "none");
        }
    }
}
