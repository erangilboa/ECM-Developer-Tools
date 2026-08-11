package com.dctm.workbench.server.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {"/", "/workbench", "/workbench/**"})
    public String index() {
        return "forward:/index.html";
    }
}
