package com.grpc.client.fileupload.client.security;

import com.grpc.client.fileupload.client.service.CustomUserDetailsService;
import com.grpc.client.fileupload.client.service.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService customUserDetailsService; // Utilisation du CustomUserDetailsService

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestPath = request.getRequestURI();

        // Ignore the filter for /register and /login endpoints
        if (requestPath.equals("/register") || requestPath.equals("/login") || requestPath.contains("/test")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            // If no Authorization header is provided, response 401 (Unauthorized)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Authorization header must be provided");
            return;
        }

        String token = authorizationHeader.substring(7);
        String username = jwtService.extractUsername(token);

        if (!jwtService.validateToken(token, username)) {
            // If the token is invalid or expired, response 401 (Unauthorized)
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid or expired token");
            return;
        }

        // Load the user details from the database
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        // Set the authentication in the SecurityContext
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        // If the token is valid, continue with the request
        filterChain.doFilter(request, response);
    }
}
