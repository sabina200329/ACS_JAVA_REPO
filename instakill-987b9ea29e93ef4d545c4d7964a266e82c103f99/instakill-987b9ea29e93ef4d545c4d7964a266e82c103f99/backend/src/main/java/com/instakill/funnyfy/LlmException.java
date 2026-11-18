package com.instakill.funnyfy;

import org.springframework.http.HttpStatus;

/** Eroare la comunicarea cu LLM-ul local, cu statusul HTTP potrivit pentru client. */
public class LlmException extends RuntimeException {

    public enum Kind {
        /** serverul nu rulează / conexiune refuzată */
        UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
        /** serverul nu a răspuns la timp */
        TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),
        /** răspuns de eroare sau invalid */
        BAD_RESPONSE(HttpStatus.BAD_GATEWAY);

        final HttpStatus status;

        Kind(HttpStatus status) {
            this.status = status;
        }
    }

    private final Kind kind;

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public HttpStatus status() {
        return kind.status;
    }
}
