package com.pronet.evaluator;

import com.pronet.evaluator.config.AppProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, AppProperties properties) throws Exception {
        http.csrf(csrf -> csrf.disable()).cors(cors -> {});
        if (properties.security().devMode())
            http.addFilterBefore(
                            new DevIdentityFilter(),
                            org.springframework.security.web.authentication
                                    .AnonymousAuthenticationFilter.class)
                    .authorizeHttpRequests(a -> a.anyRequest().authenticated());
        else
            http.authorizeHttpRequests(
                            a ->
                                    a.requestMatchers("/actuator/health")
                                            .permitAll()
                                            .anyRequest()
                                            .authenticated())
                    .oauth2ResourceServer(
                            o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtConverter())));
        return http.build();
    }

    private JwtAuthenticationConverter jwtConverter() {
        var grants = new JwtGrantedAuthoritiesConverter();
        grants.setAuthoritiesClaimName("roles");
        grants.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("email");
        converter.setJwtGrantedAuthoritiesConverter(grants);
        return converter;
    }

    static class DevIdentityFilter extends OncePerRequestFilter {
        protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain c)
                throws ServletException, IOException {
            String email = Optional.ofNullable(r.getHeader("X-User-Email")).orElse("admin@local");
            String roles =
                    Optional.ofNullable(r.getHeader("X-Roles"))
                            .orElse(
                                    "DEVELOPER,TEAM_LEAD,ENGINEERING_MANAGER,EVALUATOR_ADMIN,INTEGRATION_ADMIN,ORGANIZATION_ADMIN,AUDITOR");
            var auth =
                    Arrays.stream(roles.split(","))
                            .map(String::trim)
                            .map(x -> new SimpleGrantedAuthority("ROLE_" + x))
                            .toList();
            SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(email, "N/A", auth));
            c.doFilter(r, s);
        }
    }
}
