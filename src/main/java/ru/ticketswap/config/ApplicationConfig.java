package ru.ticketswap.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import ru.ticketswap.user.UserIdentityService;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    private final UserIdentityService userIdentityService;
    private final TicketSwapProperties ticketSwapProperties;

    public ApplicationConfig(UserIdentityService userIdentityService, TicketSwapProperties ticketSwapProperties) {
        this.userIdentityService = userIdentityService;
        this.ticketSwapProperties = ticketSwapProperties;
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return userIdentityService::loadUserDetailsByIdentifier;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService());
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Qualifier("partnerApiRestClient")
    public RestClient partnerApiRestClient() {
        TicketSwapProperties.PartnerApi properties = ticketSwapProperties.getPartnerApi();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.toIntExact(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Math.toIntExact(properties.getReadTimeoutMs()));

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
