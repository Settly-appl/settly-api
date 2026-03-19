package pl.settly.settly_api;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @GetMapping("/test")
    public void getMethodName(Authentication authentication) {
        JwtAuthenticationToken jwt = (JwtAuthenticationToken) authentication;

        System.out.println(jwt.getAuthorities());
    }
}
