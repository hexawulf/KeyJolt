package com.keyjolt.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Simple controller to return 404 for common WordPress probing paths.
 */
@Controller
public class BlockedPathController {

    @RequestMapping({"/wp-admin/**", "/wordpress/**", "/wp-login.php"})
    public ResponseEntity<Void> handleBlocked() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }
}
