package com.dctm.workbench.server.api;

import com.dctm.workbench.core.grammar.GrammarCheck;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/grammar")
public class GrammarController {

    @PostMapping("/check")
    public Dto.GrammarResponse check(@RequestBody Dto.GrammarBody body) {
        String language = body == null ? "dql" : body.language();
        String text = body == null || body.text() == null ? "" : body.text();
        return new Dto.GrammarResponse(
                GrammarCheck.check(language, text).stream().map(Dto.GrammarIssueView::of).toList());
    }
}
