package ru.ticketswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketswapBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketswapBackendApplication.class, args);
	}

}
