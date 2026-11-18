package com.instakill.funnyfy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/funnyfy  { "text": "..." }  ->  { "text": "...versiunea amuzantă..." }
 */
@RestController
@RequestMapping("/api/funnyfy")
public class FunnyfyController {

    private final FunnyfyService funnyfyService;

    public FunnyfyController(FunnyfyService funnyfyService) {
        this.funnyfyService = funnyfyService;
    }

    @PostMapping
    public FunnyfyResponse funnyfy(@Valid @RequestBody FunnyfyRequest request) {
        return new FunnyfyResponse(funnyfyService.makeFunnier(request.text()));
    }

    /** Erorile LLM-ului ajung la frontend ca mesaj clar + status potrivit (503 / 504 / 502). */
    @ExceptionHandler(LlmException.class)
    public ProblemDetail handleLlmError(LlmException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        problem.setTitle("Funnyfy unavailable");
        return problem;
    }

    public record FunnyfyRequest(@NotBlank @Size(max = 1000) String text) {
    }

    public record FunnyfyResponse(String text) {
    }
}
