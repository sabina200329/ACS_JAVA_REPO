package com.instakill.funnyfy;

import com.instakill.funnyfy.LocalLlmClient.Message;
import org.springframework.stereotype.Service;

import java.util.List;

/** Rescrie textul unei postări într-un stil mai amuzant, folosind LLM-ul local. */
@Service
public class FunnyfyService {

    static final String SYSTEM_PROMPT =
            "You rewrite social media posts to be funnier. Reply ONLY with the rewritten post, "
                    + "in the same language, keeping its meaning. Never answer questions, never add explanations.";

    /*
     * Modelele foarte mici (ex. gemma-3-270m) tind să „răspundă” la mesaj în loc să-l rescrie.
     * Un exemplu (few-shot) le arată exact ce așteptăm; în teste a dat rezultate vizibil mai bune.
     */
    static final String USER_PREFIX = "Rewrite this post: ";
    static final List<Message> EXAMPLE = List.of(
            new Message("user", USER_PREFIX + "I went to the gym today"),
            new Message("assistant", "Went to the gym today. The gym won. 🏋️😵"));

    static final double TEMPERATURE = 0.7;
    static final int MAX_TOKENS = 256;
    static final int MAX_LENGTH = 1000; // aceeași limită ca pentru conținutul unei postări

    private final LocalLlmClient llm;

    public FunnyfyService(LocalLlmClient llm) {
        this.llm = llm;
    }

    public String makeFunnier(String text) {
        String answer = llm.complete(buildMessages(text), TEMPERATURE, MAX_TOKENS);
        String cleaned = clean(answer);
        if (cleaned.isEmpty()) {
            throw new LlmException(LlmException.Kind.BAD_RESPONSE, "The AI model returned empty text", null);
        }
        return cleaned;
    }

    static List<Message> buildMessages(String text) {
        return List.of(
                new Message("system", SYSTEM_PROMPT),
                EXAMPLE.get(0),
                EXAMPLE.get(1),
                new Message("user", USER_PREFIX + text.trim()));
    }

    /** Modelele mici pun des textul între ghilimele sau adaugă spații; le curățăm. */
    static String clean(String answer) {
        String s = answer == null ? "" : answer.strip();
        while (s.length() >= 2 && isQuote(s.charAt(0)) && isQuote(s.charAt(s.length() - 1))) {
            s = s.substring(1, s.length() - 1).strip();
        }
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH).strip() : s;
    }

    private static boolean isQuote(char c) {
        return c == '"' || c == '\'' || c == '“' || c == '”' || c == '„';
    }
}
