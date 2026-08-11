package com.dctm.workbench.server.api;

import com.dctm.workbench.core.SessionException;
import com.dctm.workbench.core.UnsupportedCapabilityException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnsupportedCapabilityException.class)
    public ResponseEntity<Map<String, Object>> unsupported(UnsupportedCapabilityException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", e.getMessage(),
                "capability", e.capability().name(),
                "stub", true
        ));
    }

    @ExceptionHandler(SessionException.class)
    public ResponseEntity<Map<String, String>> session(SessionException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
